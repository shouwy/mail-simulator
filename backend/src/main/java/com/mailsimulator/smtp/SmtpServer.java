package com.mailsimulator.smtp;

import com.mailsimulator.entity.Email;
import com.mailsimulator.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SmtpServer {

    private static final Logger logger = LoggerFactory.getLogger(SmtpServer.class);

    @Value("${smtp.port:2525}")
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
        String subject = "";
        StringBuilder body = new StringBuilder();
        boolean headersDone = false;

        for (String line : rawData.split("\n")) {
            if (!headersDone) {
                if (line.trim().isEmpty()) {
                    headersDone = true;
                } else if (line.toLowerCase().startsWith("subject:")) {
                    subject = line.substring(8).trim();
                } else if (line.toLowerCase().startsWith("from:") && from.isEmpty()) {
                    from = extractAddress(line.substring(5).trim());
                } else if (line.toLowerCase().startsWith("to:") && to.isEmpty()) {
                    to = extractAddress(line.substring(3).trim());
                }
            } else {
                body.append(line).append("\n");
            }
        }

        return new Email(from, to, subject, body.toString().trim(), LocalDateTime.now());
    }
}
