# Security Policy

The PicoPOSSUM team takes the security of our application and user data seriously. This document outlines our vulnerability disclosure policy and security architecture.

---

## Supported Versions

Only the latest stable release receives active security patches.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## Reporting a Vulnerability

If you discover a security vulnerability in PicoPOSSUM, please open a GitHub issue:

### What to Include:
- A clear description of the issue.
- Step-by-step instructions or Proof of Concept (PoC) to reproduce the vulnerability.
- Potential impact (e.g. data exfiltration, privilege escalation, bypass of terminal lock).
- Any proposed remediation or patches.

### Response Timeline:
- **Initial Acknowledgement**: Within 48 hours.
- **Triage & Assessment**: Within 5 business days.
- **Patch & Advisory Release**: Coordinated with the reporter before public disclosure.

---

## Security Architecture & Design Safeguards

PicoPOSSUM is designed with defense-in-depth principles:

1. **Local-First & Zero Telemetry**:
   - All customer records, invoices, and inventory items reside exclusively on the host machine.
   - The application does not open external HTTP/WebSocket listener sockets or transmit telemetry.
2. **Content Security Policy (CSP)**:
   - Webview is locked down with strict CSP (`default-src 'self'; script-src 'self'`). Remote scripts, third-party frames, and external fonts are blocked.
3. **Password Hashing**:
   - Terminal credentials are salted and hashed using **bcrypt** with a cost factor of 10. Passwords are never stored in plaintext or recoverable formats.
4. **Ephemeral Session Management**:
   - Session tokens are stored exclusively in volatile memory (`Arc<RwLock<Option<SessionData>>>`). Closing the terminal invalidates the active session.
5. **OS Standard Storage**:
   - All databases and backup archives are written to OS-standard user directories (`$XDG_DATA_HOME` / `%APPDATA%` / `~/Library/Application Support`) to respect OS file access controls and sandboxes.
6. **Input Validation**:
   - Both the React presentation layer and the Rust backend service layers enforce strict boundary constraints on all incoming payloads before executing database transactions.
