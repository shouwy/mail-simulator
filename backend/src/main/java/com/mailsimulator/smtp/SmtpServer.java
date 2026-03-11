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
import java.net.SocketException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Locale;
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

    private static final String SMTP_READY = "220 mail-simulator SMTP Ready";
    private static final String SMTP_OK = "250 OK";
    private static final String SMTP_MESSAGE_ACCEPTED = "250 OK: Message accepted";
    private static final String SMTP_DATA_PROMPT = "354 End data with <CR><LF>.<CR><LF>";
    private static final String SMTP_BYE = "221 Bye";
    private static final String SMTP_UNKNOWN_COMMAND = "500 Unknown command";

    public SmtpServer(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostConstruct
    public void start() {
        running = true;
        executorService.submit(this::runServer);
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(smtpPort);
            logger.info("SMTP server started on port {}", smtpPort);

            while (running) {
                Socket clientSocket = acceptClient();
                if (clientSocket != null) {
                    executorService.submit(() -> handleClient(clientSocket));
                }
            }
        } catch (IOException e) {
            logger.error("Failed to start SMTP server on port {}", smtpPort, e);
        }
    }

    private Socket acceptClient() {
        try {
            return serverSocket.accept();
        } catch (SocketException e) {
            if (running) {
                logger.error("SMTP socket closed unexpectedly", e);
            }
            return null;
        } catch (IOException e) {
            if (running) {
                logger.error("Error accepting connection", e);
            }
            return null;
        }
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
            writer.println(SMTP_READY);
            ClientSession session = new ClientSession();

            String line;
            while ((line = reader.readLine()) != null) {
                if (!processClientLine(line, session, writer)) {
                    break;
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

    private boolean processClientLine(String line, ClientSession session, PrintWriter writer) {
        if (session.inData) {
            processDataLine(line, session, writer);
            return true;
        }
        return processCommandLine(line, session, writer);
    }

    private void processDataLine(String line, ClientSession session, PrintWriter writer) {
        if (".".equals(line)) {
            session.inData = false;
            Email email = parseEmail(session.from, session.to, session.dataBuffer.toString());
            emailService.save(email);
            logger.info("Email received from {} to {}", session.from, session.to);
            writer.println(SMTP_MESSAGE_ACCEPTED);
            session.dataBuffer = new StringBuilder();
            return;
        }
        session.dataBuffer.append(normalizeDotStuffedLine(line)).append("\n");
    }

    private boolean processCommandLine(String line, ClientSession session, PrintWriter writer) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
            writer.println("250-mail-simulator");
            writer.println(SMTP_OK);
            return true;
        }
        if (upper.startsWith("MAIL FROM:")) {
            session.from = extractAddress(line.substring(10));
            writer.println(SMTP_OK);
            return true;
        }
        if (upper.startsWith("RCPT TO:")) {
            session.to = extractAddress(line.substring(8));
            writer.println(SMTP_OK);
            return true;
        }
        if (upper.equals("DATA")) {
            writer.println(SMTP_DATA_PROMPT);
            session.inData = true;
            return true;
        }
        if (upper.startsWith("QUIT")) {
            writer.println(SMTP_BYE);
            return false;
        }
        if (upper.startsWith("NOOP")) {
            writer.println(SMTP_OK);
            return true;
        }
        if (upper.startsWith("RSET")) {
            session.reset();
            writer.println(SMTP_OK);
            return true;
        }
        writer.println(SMTP_UNKNOWN_COMMAND);
        return true;
    }

    private String normalizeDotStuffedLine(String line) {
        return line.startsWith("..") ? line.substring(1) : line;
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
        if (parseMultipartPart(part, email, bodyCollector, partIndex)) {
            return;
        }

        MimePartMetadata metadata = extractMimePartMetadata(part);

        if (metadata.isAttachment()) {
            addAttachmentPart(email, part, metadata);
            return;
        }

        addTextPart(email, part, bodyCollector, partIndex, metadata);
    }

    private boolean parseMultipartPart(Part part, Email email, StringBuilder bodyCollector, AtomicInteger partIndex)
        throws MessagingException, IOException {
        if (!part.isMimeType("multipart/*")) {
            return false;
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                parseMimePart(bodyPart, email, bodyCollector, partIndex);
            }
        }
        return true;
    }

    private MimePartMetadata extractMimePartMetadata(Part part) throws MessagingException {
        String rawContentType = part.getContentType();
        String contentType = extractContentType(rawContentType);
        String charset = extractCharset(rawContentType);
        String transferEncoding = readHeader(part, "Content-Transfer-Encoding");
        String disposition = part.getDisposition();
        String fileName = decodeFileName(part.getFileName());
        return new MimePartMetadata(contentType, charset, transferEncoding, disposition, fileName);
    }

    private void addAttachmentPart(Email email, Part part, MimePartMetadata metadata)
        throws MessagingException, IOException {
        email.addAttachment(new EmailAttachment(
            metadata.fileName,
            metadata.contentType,
            metadata.charset,
            metadata.transferEncoding,
            detectAttachmentSize(part)
        ));
    }

    private void addTextPart(
        Email email,
        Part part,
        StringBuilder bodyCollector,
        AtomicInteger partIndex,
        MimePartMetadata metadata
    ) throws MessagingException, IOException {
        String textContent = readPartContent(part, metadata.charset);
        email.addPart(new EmailPart(
            partIndex.getAndIncrement(),
            metadata.contentType,
            metadata.charset,
            metadata.transferEncoding,
            textContent
        ));
        appendBodyText(bodyCollector, textContent);
    }

    private void appendBodyText(StringBuilder bodyCollector, String textContent) {
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
        FallbackSections sections = splitFallbackSections(rawData);
        String subject = applyFallbackHeaders(email, sections.headerLines);

        email.setSubject(subject);
        email.setBody(String.join("\n", sections.bodyLines).trim());
        return email;
    }

    private FallbackSections splitFallbackSections(String rawData) {
        FallbackSections sections = new FallbackSections();
        boolean readingHeaders = true;

        for (String line : rawData.split("\n")) {
            if (readingHeaders && line.trim().isEmpty()) {
                readingHeaders = false;
                continue;
            }
            if (readingHeaders) {
                sections.headerLines.add(line);
            } else {
                sections.bodyLines.add(line);
            }
        }

        return sections;
    }

    private String applyFallbackHeaders(Email email, java.util.List<String> headerLines) {
        String subject = "";
        for (String headerLine : headerLines) {
            String lower = headerLine.toLowerCase(Locale.ROOT);
            if (lower.startsWith("subject:")) {
                subject = headerLine.substring(8).trim();
            } else if (lower.startsWith("from:") && email.getFrom().isEmpty()) {
                email.setFrom(extractAddress(headerLine.substring(5).trim()));
            } else if (lower.startsWith("to:") && email.getTo().isEmpty()) {
                email.setTo(extractAddress(headerLine.substring(3).trim()));
            }
        }
        return subject;
    }

    private static final class ClientSession {
        private String from = "";
        private String to = "";
        private StringBuilder dataBuffer = new StringBuilder();
        private boolean inData = false;

        private void reset() {
            from = "";
            to = "";
            dataBuffer = new StringBuilder();
            inData = false;
        }
    }

    private static final class FallbackSections {
        private final java.util.List<String> headerLines = new java.util.ArrayList<>();
        private final java.util.List<String> bodyLines = new java.util.ArrayList<>();
    }

    private static final class MimePartMetadata {
        private final String contentType;
        private final String charset;
        private final String transferEncoding;
        private final String disposition;
        private final String fileName;

        private MimePartMetadata(String contentType, String charset, String transferEncoding, String disposition, String fileName) {
            this.contentType = contentType;
            this.charset = charset;
            this.transferEncoding = transferEncoding;
            this.disposition = disposition;
            this.fileName = fileName;
        }

        private boolean isAttachment() {
            return Part.ATTACHMENT.equalsIgnoreCase(disposition) || (fileName != null && !fileName.isBlank());
        }
    }
}
