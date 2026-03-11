package com.mailsimulator;

import com.mailsimulator.entity.Email;
import com.mailsimulator.entity.EmailAttachment;
import com.mailsimulator.entity.EmailPart;
import com.mailsimulator.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;
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
    void shouldDecodeEncodedSubjectWhenMultipartEmailIsReceived() throws Exception {
        Email stored = sendMultipartEmailAndFetchStored();

        assertNotNull(stored);
        assertEquals("sender@example.com", stored.getFrom());
        assertEquals("receiver@example.com", stored.getTo());
        assertEquals("Réunion ✓", stored.getSubject());
    }

    @Test
    void shouldExtractAllMultipartTextPartsWhenMultipartEmailIsReceived() throws Exception {
        Email stored = sendMultipartEmailAndFetchStored();

        List<EmailPart> parts = stored.getParts().stream()
            .sorted(Comparator.comparing(EmailPart::getPartIndex))
            .toList();

        assertEquals(3, parts.size());
        assertPart(parts.get(0), "text/plain", "UTF-8", "quoted-printable", "Bonjour équipe");
        assertPart(parts.get(1), "text/html", "UTF-8", "quoted-printable", "Réunion");
        assertPart(parts.get(2), "text/calendar", "UTF-8", "8bit", "BEGIN:VCALENDAR");
    }

    @Test
    void shouldStoreAttachmentMetadataWhenMultipartEmailIsReceived() throws Exception {
        Email stored = sendMultipartEmailAndFetchStored();

        assertEquals(1, stored.getAttachments().size());
        EmailAttachment attachment = stored.getAttachments().getFirst();
        assertEquals("pièce.pdf", attachment.getFileName());
        assertEquals("application/pdf", attachment.getContentType());
        assertEquals("base64", attachment.getTransferEncoding());
        assertTrue(attachment.getSizeBytes() > 0);
    }

    @Test
    void shouldAggregateMultipartTextPartsIntoBodyWhenMultipartEmailIsReceived() throws Exception {
        Email stored = sendMultipartEmailAndFetchStored();

        assertFalse(stored.getBody().isBlank());
        assertTrue(stored.getBody().contains("Bonjour équipe"));
        assertTrue(stored.getBody().contains("BEGIN:VCALENDAR"));
    }

    @Test
    void shouldResetEnvelopeWhenRsetIsSent() throws Exception {
        try (SmtpTestClient client = new SmtpTestClient()) {
            client.expectGreeting();
            client.sendHelo("EHLO localhost");

            client.sendMailFrom("first@example.com");
            client.sendRcptTo("first-recipient@example.com");

            client.sendRset();

            client.sendMailFrom("second@example.com");
            client.sendRcptTo("second-recipient@example.com");

            client.startData();
            client.sendDataLine("Subject: Envelope reset test");
            client.sendDataLine("");
            client.sendDataLine("Body after RSET");
            client.finishData();
            client.quit();
        }

        Email stored = waitForSingleEmail(Duration.ofSeconds(5));
        assertNotNull(stored);
        assertEquals("second@example.com", stored.getFrom());
        assertEquals("second-recipient@example.com", stored.getTo());
        assertEquals("Envelope reset test", stored.getSubject());
        assertTrue(stored.getBody().contains("Body after RSET"));
    }

    @Test
    void shouldUnescapeDotStuffedLinesWhenDataSectionIsParsed() throws Exception {
        try (SmtpTestClient client = new SmtpTestClient()) {
            client.expectGreeting();
            client.sendHelo("HELO localhost");

            client.sendMailFrom("sender@example.com");
            client.sendRcptTo("receiver@example.com");

            client.startData();
            client.sendDataLine("Subject: Dot test");
            client.sendDataLine("");
            client.sendDataLine("..line-starts-with-dot");
            client.sendDataLine("regular-line");
            client.finishData();
            client.quit();
        }

        Email stored = waitForSingleEmail(Duration.ofSeconds(5));
        assertNotNull(stored);
        assertTrue(stored.getBody().contains(".line-starts-with-dot"));
        assertFalse(stored.getBody().contains("..line-starts-with-dot"));
        assertTrue(stored.getBody().contains("regular-line"));
    }

    private Email waitForSingleEmail(Duration timeout) {
        final java.util.concurrent.atomic.AtomicReference<Email> storedEmail = new java.util.concurrent.atomic.AtomicReference<>();
        Awaitility.await("one stored email")
            .atMost(timeout)
            .pollInterval(Duration.ofMillis(100))
            .until(() -> {
                List<Email> emails = emailRepository.findAll();
                if (emails.size() == 1) {
                    storedEmail.set(emails.getFirst());
                    return true;
                }
                return false;
            });
        return storedEmail.get();
    }

    private Email sendMultipartEmailAndFetchStored() throws Exception {
        try (SmtpTestClient client = new SmtpTestClient()) {
            client.expectGreeting();
            client.sendHelo("EHLO localhost");
            client.sendMailFrom("sender@example.com");
            client.sendRcptTo("receiver@example.com");
            client.startData();

            client.sendDataLine("From: sender@example.com");
            client.sendDataLine("To: receiver@example.com");
            client.sendDataLine("Subject: =?UTF-8?Q?R=C3=A9union_=E2=9C=93?=");
            client.sendDataLine("MIME-Version: 1.0");
            client.sendDataLine("Content-Type: multipart/mixed; boundary=\"mixed-boundary\"");
            client.sendDataLine("");

            client.sendDataLine("--mixed-boundary");
            client.sendDataLine("Content-Type: text/plain; charset=UTF-8");
            client.sendDataLine("Content-Transfer-Encoding: quoted-printable");
            client.sendDataLine("");
            client.sendDataLine("Bonjour =C3=A9quipe");
            client.sendDataLine("");

            client.sendDataLine("--mixed-boundary");
            client.sendDataLine("Content-Type: text/html; charset=UTF-8");
            client.sendDataLine("Content-Transfer-Encoding: quoted-printable");
            client.sendDataLine("");
            client.sendDataLine("<html><body><p>R=C3=A9union <b>importante</b></p></body></html>");
            client.sendDataLine("");

            client.sendDataLine("--mixed-boundary");
            client.sendDataLine("Content-Type: text/calendar; charset=UTF-8; method=REQUEST");
            client.sendDataLine("Content-Transfer-Encoding: 8bit");
            client.sendDataLine("");
            client.sendDataLine("BEGIN:VCALENDAR");
            client.sendDataLine("VERSION:2.0");
            client.sendDataLine("BEGIN:VEVENT");
            client.sendDataLine("SUMMARY:Test Calendar");
            client.sendDataLine("END:VEVENT");
            client.sendDataLine("END:VCALENDAR");
            client.sendDataLine("");

            client.sendDataLine("--mixed-boundary");
            client.sendDataLine("Content-Type: application/pdf; name=\"=?UTF-8?Q?pi=C3=A8ce.pdf?=\"");
            client.sendDataLine("Content-Disposition: attachment; filename=\"=?UTF-8?Q?pi=C3=A8ce.pdf?=\"");
            client.sendDataLine("Content-Transfer-Encoding: base64");
            client.sendDataLine("");
            client.sendDataLine("SGVsbG8gYXR0YWNobWVudA==");
            client.sendDataLine("");

            client.sendDataLine("--mixed-boundary--");
            client.finishData();
            client.quit();
        }
        return waitForSingleEmail(Duration.ofSeconds(5));
    }

    private void assertPart(
        EmailPart part,
        String expectedContentType,
        String expectedCharset,
        String expectedTransferEncoding,
        String expectedContentSnippet
    ) {
        assertEquals(expectedContentType, part.getContentType());
        assertEquals(expectedCharset, part.getCharset());
        assertEquals(expectedTransferEncoding, part.getTransferEncoding());
        assertTrue(part.getContent().contains(expectedContentSnippet));
    }

    private static final class SmtpTestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private SmtpTestClient() throws Exception {
            socket = new Socket("127.0.0.1", 2626);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        }

        private void expectGreeting() throws Exception {
            assertReplyStartsWith("220");
        }

        private void sendHelo(String heloCommand) throws Exception {
            sendLine(heloCommand);
            assertReplyStartsWith("250-");
            assertReplyStartsWith("250");
        }

        private void sendMailFrom(String address) throws Exception {
            sendLine("MAIL FROM:<" + address + ">");
            assertReplyStartsWith("250");
        }

        private void sendRcptTo(String address) throws Exception {
            sendLine("RCPT TO:<" + address + ">");
            assertReplyStartsWith("250");
        }

        private void sendRset() throws Exception {
            sendLine("RSET");
            assertReplyStartsWith("250");
        }

        private void startData() throws Exception {
            sendLine("DATA");
            assertReplyStartsWith("354");
        }

        private void finishData() throws Exception {
            sendLine(".");
            assertReplyStartsWith("250");
        }

        private void quit() throws Exception {
            sendLine("QUIT");
            assertReplyStartsWith("221");
        }

        private void sendDataLine(String value) throws Exception {
            sendLine(value);
        }

        private void sendLine(String value) throws Exception {
            writer.write(value);
            writer.write("\r\n");
            writer.flush();
        }

        private void assertReplyStartsWith(String expectedPrefix) throws Exception {
            String line = reader.readLine();
            assertNotNull(line);
            assertTrue(line.startsWith(expectedPrefix));
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
