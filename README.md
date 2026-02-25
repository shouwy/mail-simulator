# mail-simulator

A complete application to simulate an SMTP server and visualize received emails through a web interface.

## Architecture

- **Backend**: Spring Boot 3.3.5 — embeds an SMTP server on port 2525 and exposes a REST API on port 8080. Emails are persisted in an in-memory H2 database.
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

The REST API is available at `http://localhost:8080/api/emails`.  
The H2 console is available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:maildb`).

### Frontend

```bash
cd frontend
npm install
ng serve
```

Open `http://localhost:4200` in your browser.

## Sending a Test Email

Point any SMTP client at `localhost:2525` (no authentication required). Example with Python:

```python
import smtplib
from email.mime.text import MIMEText

msg = MIMEText("Hello from the simulator!")
msg["Subject"] = "Test"
msg["From"] = "sender@example.com"
msg["To"] = "recipient@example.com"

with smtplib.SMTP("localhost", 2525) as server:
    server.sendmail("sender@example.com", ["recipient@example.com"], msg.as_string())
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/emails` | List all received emails |
| GET | `/api/emails/{id}` | Get a single email by ID |
