# Changelog

All notable changes to this project are documented in this file.

## 2026-03-11

### Added

- MIME-aware SMTP parsing in backend.
- Encoded subject decoding support (RFC 2047).
- Multipart parsing with per-part metadata persistence: `Content-Type`, `charset`, `Content-Transfer-Encoding`.
- Attachment metadata persistence: file name (decoded when encoded), content type, charset, transfer encoding, size in bytes.
- Frontend detail view support for multipart and attachment metadata.
- Frontend part-by-part rendering with `HTML` / `TEXT` / `OTHER` badges.
- Frontend sorting of parts with `text/html` first, then `text/plain`, then other content types.
- Real SMTP integration test: `backend/src/test/java/com/mailsimulator/SmtpServerIntegrationTest.java`.

### Changed

- Default ports updated to avoid local conflicts:
  - Frontend: `4300`
  - Backend API: `8090`
  - SMTP: `4025`
- Frontend API endpoint updated to `http://localhost:8090/api/emails`.
- Project documentation updated in `README.md`, `backend/README.md`, and `frontend/README.md`.

### Fixed

- Correct handling of MIME-encoded subjects and multipart message parsing in real SMTP flows.
