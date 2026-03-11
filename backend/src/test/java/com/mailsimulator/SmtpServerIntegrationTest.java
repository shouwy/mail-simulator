package com.mailsimulator;

import com.mailsimulator.entity.Email;
import com.mailsimulator.entity.EmailAttachment;
import com.mailsimulator.entity.EmailPart;
import com.mailsimulator.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "smtp.port=2626")
class SmtpServerIntegrationTest {

    @Autowired
    private EmailRepository emailRepository;

    @BeforeEach
    void setUp() {
        emailRepository.deleteAll();
    }

    @Test
    void shouldParseMultipartAndAttachmentAndEncodedSubject() throws Exception {
        try (
            Socket socket = new Socket("127.0.0.1", 2626);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))
        ) {
            assertTrue(reader.readLine().startsWith("220"));

            sendCommand(writer, "EHLO localhost");
            assertTrue(reader.readLine().startsWith("250-"));
            assertTrue(reader.readLine().startsWith("250"));

            sendCommand(writer, "MAIL FROM:<sender@example.com>");
            assertTrue(reader.readLine().startsWith("250"));

            sendCommand(writer, "RCPT TO:<receiver@example.com>");
            assertTrue(reader.readLine().startsWith("250"));

            sendCommand(writer, "DATA");
            assertTrue(reader.readLine().startsWith("354"));

            sendDataLine(writer, "From: sender@example.com");
            sendDataLine(writer, "To: receiver@example.com");
            sendDataLine(writer, "Subject: =?UTF-8?Q?R=C3=A9union_=E2=9C=93?=");
            sendDataLine(writer, "MIME-Version: 1.0");
            sendDataLine(writer, "Content-Type: multipart/mixed; boundary=\"mixed-boundary\"");
            sendDataLine(writer, "");

            sendDataLine(writer, "--mixed-boundary");
            sendDataLine(writer, "Content-Type: text/plain; charset=UTF-8");
            sendDataLine(writer, "Content-Transfer-Encoding: quoted-printable");
            sendDataLine(writer, "");
            sendDataLine(writer, "Bonjour =C3=A9quipe");
            sendDataLine(writer, "");

            sendDataLine(writer, "--mixed-boundary");
            sendDataLine(writer, "Content-Type: text/html; charset=UTF-8");
            sendDataLine(writer, "Content-Transfer-Encoding: quoted-printable");
            sendDataLine(writer, "");
            sendDataLine(writer, "<html><body><p>R=C3=A9union <b>importante</b></p></body></html>");
            sendDataLine(writer, "");

            sendDataLine(writer, "--mixed-boundary");
            sendDataLine(writer, "Content-Type: text/calendar; charset=UTF-8; method=REQUEST");
            sendDataLine(writer, "Content-Transfer-Encoding: 8bit");
            sendDataLine(writer, "");
            sendDataLine(writer, "BEGIN:VCALENDAR");
            sendDataLine(writer, "VERSION:2.0");
            sendDataLine(writer, "BEGIN:VEVENT");
            sendDataLine(writer, "SUMMARY:Test Calendar");
            sendDataLine(writer, "END:VEVENT");
            sendDataLine(writer, "END:VCALENDAR");
            sendDataLine(writer, "");

            sendDataLine(writer, "--mixed-boundary");
            sendDataLine(writer, "Content-Type: application/pdf; name=\"=?UTF-8?Q?pi=C3=A8ce.pdf?=\"");
            sendDataLine(writer, "Content-Disposition: attachment; filename=\"=?UTF-8?Q?pi=C3=A8ce.pdf?=\"");
            sendDataLine(writer, "Content-Transfer-Encoding: base64");
            sendDataLine(writer, "");
            sendDataLine(writer, "SGVsbG8gYXR0YWNobWVudA==");
            sendDataLine(writer, "");

            sendDataLine(writer, "--mixed-boundary--");
            sendCommand(writer, ".");
            assertTrue(reader.readLine().startsWith("250"));

            sendCommand(writer, "QUIT");
            assertTrue(reader.readLine().startsWith("221"));
        }

        Email stored = waitForSingleEmail(Duration.ofSeconds(5));
        assertNotNull(stored);

        assertEquals("sender@example.com", stored.getFrom());
        assertEquals("receiver@example.com", stored.getTo());
        assertEquals("Réunion ✓", stored.getSubject());

        List<EmailPart> parts = stored.getParts().stream()
            .sorted(Comparator.comparing(EmailPart::getPartIndex))
            .toList();
        assertEquals(3, parts.size());

        assertEquals("text/plain", parts.get(0).getContentType());
        assertEquals("UTF-8", parts.get(0).getCharset());
        assertEquals("quoted-printable", parts.get(0).getTransferEncoding());
        assertTrue(parts.get(0).getContent().contains("Bonjour équipe"));

        assertEquals("text/html", parts.get(1).getContentType());
        assertEquals("UTF-8", parts.get(1).getCharset());
        assertEquals("quoted-printable", parts.get(1).getTransferEncoding());
        assertTrue(parts.get(1).getContent().contains("Réunion"));

        assertEquals("text/calendar", parts.get(2).getContentType());
        assertEquals("UTF-8", parts.get(2).getCharset());
        assertEquals("8bit", parts.get(2).getTransferEncoding());
        assertTrue(parts.get(2).getContent().contains("BEGIN:VCALENDAR"));

        assertEquals(1, stored.getAttachments().size());
        EmailAttachment attachment = stored.getAttachments().getFirst();
        assertEquals("pièce.pdf", attachment.getFileName());
        assertEquals("application/pdf", attachment.getContentType());
        assertEquals("base64", attachment.getTransferEncoding());
        assertTrue(attachment.getSizeBytes() > 0);

        assertFalse(stored.getBody().isBlank());
        assertTrue(stored.getBody().contains("Bonjour équipe"));
        assertTrue(stored.getBody().contains("BEGIN:VCALENDAR"));
    }

    private Email waitForSingleEmail(Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            List<Email> emails = emailRepository.findAll();
            if (emails.size() == 1) {
                return emails.getFirst();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for one stored email");
    }

    private void sendCommand(BufferedWriter writer, String value) throws Exception {
        writer.write(value);
        writer.write("\r\n");
        writer.flush();
    }

    private void sendDataLine(BufferedWriter writer, String value) throws Exception {
        writer.write(value);
        writer.write("\r\n");
        writer.flush();
    }
}
