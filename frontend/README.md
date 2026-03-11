# Frontend

Angular 19 UI for browsing emails received by the SMTP simulator backend.

## Features

- Email list with sender, recipient, subject, and reception date.
- Email detail page with:
	- per-part message rendering (`text/html`, `text/plain`, and other content types)
	- HTML rendering for `text/html` parts
	- type badges per part: `HTML`, `TEXT`, `OTHER`
	- part ordering optimized for readability (`text/html` first)
	- multipart part metadata (`contentType`, `charset`, `transferEncoding`)
	- attachment metadata (name, type, charset, transfer encoding, size)

## Run Locally

```bash
cd frontend
npm install
npm run start
```

Open `http://localhost:4300`.

The backend API is expected at `http://localhost:8090/api/emails`.

## Build

```bash
cd frontend
npm run build
```

On Windows PowerShell environments with script execution restrictions, use:

```bash
npm.cmd run build
```

## Test

```bash
cd frontend
npm test
```
