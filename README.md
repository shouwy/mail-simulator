# mail-simulator

A complete application to simulate an SMTP server and visualize received emails through a web interface.

## What's New

- MIME-aware SMTP parsing with support for:
    - encoded subjects (RFC 2047)
    - `multipart/*` messages (including nested multiparts)
    - per-part `Content-Type`, `charset`, and `Content-Transfer-Encoding`
    - attachment detection and metadata extraction (file name, content type, transfer encoding, size)
- Email detail UI now renders message content per part with:
    - HTML rendering for `text/html` parts
    - plain preview for `text/plain` and other part types
    - `HTML` / `TEXT` / `OTHER` badges for quick identification
    - prioritized ordering (`text/html`, then `text/plain`, then other types)
    - multipart part metadata and attachment metadata
- End-to-end SMTP integration test added to validate real wire-level behavior.
- Backend SMTP server code was refactored to reduce cognitive complexity and improve maintainability (`start`, SMTP command handling, fallback parsing, MIME part parsing).
- SMTP integration coverage was expanded with:
    - `RSET` envelope reset behavior
    - SMTP dot-stuffing handling in `DATA`
- Frontend code quality improvements:
    - `readonly` dependency injections in components/services
    - simplified loading state handling with RxJS `finalize`
    - keyboard-accessible email row navigation
- Frontend unit tests now cover components and service behavior (list/detail/service).

## Architecture

- **Backend**: Spring Boot 3.5.11 — embeds an SMTP server on port 4025 and exposes a REST API on port 8090. Emails are persisted in an in-memory H2 database.
- **Frontend**: Angular 19 — lists received emails and shows the full detail of any email with a single click.

## Screenshots

### Email List
![Email List](https://github.com/user-attachments/assets/127150b8-a87e-453a-8467-8045186c06df)

### Email Detail
![Email Detail](https://github.com/user-attachments/assets/9f823727-5628-4d80-8da2-6079fa1dbdd8)

## Getting Started

### Backend

```bash
cd backend
mvn spring-boot:run
```

The REST API is available at `http://localhost:8090/api/emails`.  
The H2 console is available at `http://localhost:8090/h2-console` (JDBC URL: `jdbc:h2:mem:maildb`).

### Frontend

```bash
cd frontend
npm install
ng serve --port 4300
```

Open `http://localhost:4300` in your browser.

## Sending a Test Email

Point any SMTP client at `localhost:4025` (no authentication required). Example with Python:

```python
import smtplib
from email.mime.text import MIMEText

msg = MIMEText("Hello from the simulator!")
msg["Subject"] = "Test"
msg["From"] = "sender@example.com"
msg["To"] = "recipient@example.com"

with smtplib.SMTP("localhost", 4025) as server:
    server.sendmail("sender@example.com", ["recipient@example.com"], msg.as_string())
```

### Multipart + Attachment Example (Python)

```python
import smtplib
from email.message import EmailMessage

msg = EmailMessage()
msg["Subject"] = "Reunion ✓"
msg["From"] = "sender@example.com"
msg["To"] = "recipient@example.com"

msg.set_content("Bonjour equipe")
msg.add_alternative("<html><body><p>Bonjour <b>equipe</b></p></body></html>", subtype="html")

calendar_payload = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
SUMMARY:Demo Calendar
END:VEVENT
END:VCALENDAR
"""
msg.add_attachment(
    calendar_payload.encode("utf-8"),
    maintype="text",
    subtype="calendar",
    params={"charset": "UTF-8", "method": "REQUEST"}
)

msg.add_attachment(
    b"Hello attachment",
    maintype="application",
    subtype="octet-stream",
    filename="piece-jointe.txt"
)

with smtplib.SMTP("localhost", 4025) as server:
    server.send_message(msg)
```

## Integration Test

The backend contains a real integration test that opens a TCP SMTP connection and sends a raw multipart MIME message to the embedded SMTP server:

- Test file: `backend/src/test/java/com/mailsimulator/SmtpServerIntegrationTest.java`
- Run backend tests:

```bash
cd backend
mvn test
```

It validates:

- encoded subject decoding
- multipart parsing (`text/plain`, `text/html`, `text/calendar`)
- `charset` and `Content-Transfer-Encoding` preservation per part
- attachment metadata extraction

## Frontend Unit Tests

The frontend test suite runs with Karma + Firefox Headless.

- Main specs:
    - `frontend/src/app/components/email-list/email-list.component.spec.ts`
    - `frontend/src/app/components/email-detail/email-detail.component.spec.ts`
    - `frontend/src/app/services/email.service.spec.ts`

Run tests:

```bash
cd frontend
npm test -- --watch=false --browsers=FirefoxHeadless --progress=false
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/emails` | List all received emails |
| GET | `/api/emails/{id}` | Get a single email by ID |

## Changelog

See `CHANGELOG.md` for the complete history.

### 2026-03-11

#### Added

- MIME-aware SMTP parsing in backend.
- Encoded subject decoding support (RFC 2047).
- Multipart parsing with per-part metadata persistence: `Content-Type`, `charset`, `Content-Transfer-Encoding`.
- Attachment metadata persistence: file name (decoded when encoded), content type, charset, transfer encoding, size in bytes.
- Frontend detail view support for multipart and attachment metadata.
- Frontend part-by-part rendering with `HTML` / `TEXT` / `OTHER` badges.
- Frontend sorting of parts with `text/html` first, then `text/plain`, then other content types.
- Real SMTP integration test: `backend/src/test/java/com/mailsimulator/SmtpServerIntegrationTest.java`.

#### Changed

- Project documentation updated in `README.md`, `backend/README.md`, and `frontend/README.md`.

#### Fixed

- Correct handling of MIME-encoded subjects and multipart message parsing in real SMTP flows.
