# Backend

Spring Boot backend for the SMTP mail simulator.

## Capabilities

- Embedded SMTP server listening on port `2525` by default.
- REST API on port `8080`.
- Email persistence in H2 in-memory database.
- MIME-aware parsing:
  - encoded subject decoding (`=?UTF-8?...?=`)
  - multipart parsing (`multipart/*`, including nested parts)
  - per-part metadata extraction (`Content-Type`, `charset`, `Content-Transfer-Encoding`)
  - attachment metadata extraction (file name, type, charset, transfer encoding, size)

## Run

```bash
cd backend
mvn spring-boot:run
```

Useful endpoints:

- API: `http://localhost:8080/api/emails`
- H2 console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:maildb`

## Tests

### Full test suite

```bash
cd backend
mvn test
```

### SMTP integration test

`src/test/java/com/mailsimulator/SmtpServerIntegrationTest.java` sends a real raw MIME email over TCP SMTP and validates:

- encoded subject decoding
- multipart content extraction (`text/plain`, `text/html`, `text/calendar`)
- `charset` and transfer-encoding persistence per part
- attachment metadata persistence
