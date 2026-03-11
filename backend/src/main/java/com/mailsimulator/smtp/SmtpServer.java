package com.mailsimulator.smtp;

import com.mailsimulator.entity.EmailAttachment;
import com.mailsimulator.entity.EmailPart;
import com.mailsimulator.entity.Email;
import com.mailsimulator.service.EmailService;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SmtpServer {

    private static final Logger logger = LoggerFactory.getLogger(SmtpServer.class);

    @Value("${smtp.port:4025}")
    private int smtpPort;

    private final EmailService emailService;
    private ServerSocket serverSocket;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    public SmtpServer(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostConstruct
    public void start() {
        running = true;
        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(smtpPort);
                logger.info("SMTP server started on port {}", smtpPort);
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executorService.submit(() -> handleClient(clientSocket));
                    } catch (IOException e) {
                        if (running) {
                            logger.error("Error accepting connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to start SMTP server on port {}", smtpPort, e);
            }
        });
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing SMTP server socket", e);
        }
        executorService.shutdownNow();
        logger.info("SMTP server stopped");
    }

    private void handleClient(Socket socket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            writer.println("220 mail-simulator SMTP Ready");

            String from = "";
            String to = "";
            StringBuilder dataBuffer = new StringBuilder();
            boolean inData = false;

            String line;
            while ((line = reader.readLine()) != null) {
                if (inData) {
                    if (line.equals(".")) {
                        inData = false;
                        Email email = parseEmail(from, to, dataBuffer.toString());
                        emailService.save(email);
                        logger.info("Email received from {} to {}", from, to);
                        writer.println("250 OK: Message accepted");
                        dataBuffer = new StringBuilder();
                    } else {
                        dataBuffer.append(line.startsWith("..") ? line.substring(1) : line).append("\n");
                    }
                } else {
                    String upper = line.toUpperCase();
                    if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
                        writer.println("250-mail-simulator");
                        writer.println("250 OK");
                    } else if (upper.startsWith("MAIL FROM:")) {
                        from = extractAddress(line.substring(10));
                        writer.println("250 OK");
                    } else if (upper.startsWith("RCPT TO:")) {
                        to = extractAddress(line.substring(8));
                        writer.println("250 OK");
                    } else if (upper.equals("DATA")) {
                        writer.println("354 End data with <CR><LF>.<CR><LF>");
                        inData = true;
                    } else if (upper.startsWith("QUIT")) {
                        writer.println("221 Bye");
                        break;
                    } else if (upper.startsWith("NOOP")) {
                        writer.println("250 OK");
                    } else if (upper.startsWith("RSET")) {
                        from = "";
                        to = "";
                        dataBuffer = new StringBuilder();
                        writer.println("250 OK");
                    } else {
                        writer.println("500 Unknown command");
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error handling SMTP client", e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }

    private String extractAddress(String raw) {
        raw = raw.trim();
        int start = raw.indexOf('<');
        int end = raw.indexOf('>');
        if (start >= 0 && end > start) {
            return raw.substring(start + 1, end);
        }
        return raw;
    }

    private Email parseEmail(String from, String to, String rawData) {
        Email email = new Email();
        email.setFrom(from);
        email.setTo(to);
        email.setReceivedAt(LocalDateTime.now());

        try {
            MimeMessage message = buildMimeMessage(rawData);
            String decodedSubject = decodeSubject(message);
            email.setSubject(decodedSubject);

            if (email.getFrom().isEmpty()) {
                email.setFrom(resolveAddress(message.getFrom()));
            }
            if (email.getTo().isEmpty()) {
                email.setTo(resolveAddress(message.getRecipients(Message.RecipientType.TO)));
            }

            StringBuilder bodyCollector = new StringBuilder();
            AtomicInteger partIndex = new AtomicInteger(0);
            parseMimePart(message, email, bodyCollector, partIndex);

            if (bodyCollector.isEmpty()) {
                email.setBody("");
            } else {
                email.setBody(bodyCollector.toString().trim());
            }

            return email;
        } catch (Exception e) {
            logger.warn("MIME parsing failed, using fallback parser", e);
            return fallbackParseEmail(email, rawData);
        }
    }

    private MimeMessage buildMimeMessage(String rawData) throws MessagingException {
        String normalized = rawData.replace("\r\n", "\n").replace("\n", "\r\n");
        Session session = Session.getInstance(new Properties());
        return new MimeMessage(session, new ByteArrayInputStream(normalized.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private String decodeSubject(MimeMessage message) throws MessagingException {
        String rawSubject = message.getHeader("Subject", null);
        if (rawSubject == null || rawSubject.isBlank()) {
            return Objects.toString(message.getSubject(), "").trim();
        }
        try {
            return MimeUtility.decodeText(rawSubject.replace("\r", "").replace("\n", "")).trim();
        } catch (UnsupportedEncodingException e) {
            logger.debug("Could not decode encoded subject '{}', keeping raw value", rawSubject);
            return rawSubject.trim();
        }
    }

    private void parseMimePart(Part part, Email email, StringBuilder bodyCollector, AtomicInteger partIndex)
        throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart bodyPart = multipart.getBodyPart(i);
                    parseMimePart(bodyPart, email, bodyCollector, partIndex);
                }
            }
            return;
        }

        String contentType = extractContentType(part.getContentType());
        String charset = extractCharset(part.getContentType());
        String transferEncoding = readHeader(part, "Content-Transfer-Encoding");
        String disposition = part.getDisposition();
        String fileName = decodeFileName(part.getFileName());
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(disposition) || (fileName != null && !fileName.isBlank());

        if (attachment) {
            email.addAttachment(new EmailAttachment(
                fileName,
                contentType,
                charset,
                transferEncoding,
                detectAttachmentSize(part)
            ));
            return;
        }

        String textContent = readPartContent(part, charset);
        email.addPart(new EmailPart(
            partIndex.getAndIncrement(),
            contentType,
            charset,
            transferEncoding,
            textContent
        ));

        if (textContent != null && !textContent.isBlank()) {
            if (!bodyCollector.isEmpty()) {
                bodyCollector.append("\n\n");
            }
            bodyCollector.append(textContent.trim());
        }
    }

    private String readPartContent(Part part, String charsetName) throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof InputStream stream) {
            byte[] bytes = stream.readAllBytes();
            Charset charset = resolveCharset(charsetName);
            return new String(bytes, charset);
        }
        return "";
    }

    private long detectAttachmentSize(Part part) throws MessagingException, IOException {
        if (part.getSize() >= 0) {
            return part.getSize();
        }
        try (InputStream inputStream = part.getInputStream()) {
            return inputStream.readAllBytes().length;
        }
    }

    private Charset resolveCharset(String charsetName) {
        if (charsetName == null || charsetName.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charsetName);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private String extractContentType(String rawContentType) {
        try {
            return new ContentType(rawContentType).getBaseType();
        } catch (Exception e) {
            return rawContentType;
        }
    }

    private String extractCharset(String rawContentType) {
        try {
            return new ContentType(rawContentType).getParameter("charset");
        } catch (Exception e) {
            return null;
        }
    }

    private String readHeader(Part part, String headerName) throws MessagingException {
        String[] values = part.getHeader(headerName);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private String decodeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(fileName);
        } catch (UnsupportedEncodingException e) {
            return fileName;
        }
    }

    private String resolveAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        Address first = addresses[0];
        if (first instanceof InternetAddress internetAddress) {
            return internetAddress.getAddress();
        }
        return first.toString();
    }

    private Email fallbackParseEmail(Email email, String rawData) {
        String subject = "";
        StringBuilder body = new StringBuilder();
        boolean headersDone = false;

        for (String line : rawData.split("\n")) {
            if (!headersDone) {
                if (line.trim().isEmpty()) {
                    headersDone = true;
                } else if (line.toLowerCase().startsWith("subject:")) {
                    subject = line.substring(8).trim();
                } else if (line.toLowerCase().startsWith("from:") && email.getFrom().isEmpty()) {
                    email.setFrom(extractAddress(line.substring(5).trim()));
                } else if (line.toLowerCase().startsWith("to:") && email.getTo().isEmpty()) {
                    email.setTo(extractAddress(line.substring(3).trim()));
                }
            } else {
                body.append(line).append("\n");
            }
        }

        email.setSubject(subject);
        email.setBody(body.toString().trim());
        return email;
    }
}
