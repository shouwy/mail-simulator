# Frontend

Angular 19 UI for browsing emails received by the SMTP simulator backend.

## Features

- Email list with sender, recipient, subject, and reception date.
- Email detail page with:
	- aggregated body preview
	- multipart part metadata (`contentType`, `charset`, `transferEncoding`)
	- attachment metadata (name, type, charset, transfer encoding, size)

## Run Locally

```bash
cd frontend
npm install
npm run start
```

Open `http://localhost:4200`.

The backend API is expected at `http://localhost:8080/api/emails`.

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
