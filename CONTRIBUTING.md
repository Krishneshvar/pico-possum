# Contributing to PicoPOSSUM

Thank you for your interest in contributing to PicoPOSSUM! We welcome bug reports, documentation updates, feature requests, and code contributions.

---

## 1. Code of Conduct

We are committed to providing a welcoming, inclusive, and harassment-free environment for all participants. Please treat all contributors with respect and professionalism.

---

## 2. Prerequisites & Environment Setup

To build and run PicoPOSSUM from source, ensure your environment meets the following requirements:

### Required Toolchains
- **Node.js**: `>= 20.0.0` (LTS recommended)
- **Rust**: `>= 1.77.0` (stable)
- **Cargo**: Standard Rust package manager

### System Dependencies

#### Linux (Debian / Ubuntu)
```bash
sudo apt update
sudo apt install -y libwebkit2gtk-4.1-dev build-essential curl wget file \
  libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev
```

#### Linux (Fedora / RHEL)
```bash
sudo dnf check-update
sudo dnf install -y webkit2gtk4.1-devel openssl-devel curl wget file \
  libappindicator-gtk3-devel librsvg2-devel
```

#### macOS
- Xcode Command Line Tools: `xcode-select --install`

#### Windows
- Microsoft C++ Build Tools (with Desktop development with C++)
- WebView2 Runtime (preinstalled on Windows 10/11)

---

## 3. Getting Started

1. **Fork and Clone the Repository**:
   ```bash
   git clone https://github.com/<your-username>/pico-possum.git
   cd pico-possum
   ```

2. **Install Frontend Dependencies**:
   ```bash
   npm install
   ```

3. **Run in Development Mode**:
   ```bash
   npm run tauri dev
   ```
   This command starts the Vite dev server and launches the native desktop window with hot module replacement (HMR).

---

## 4. Architectural Guidelines

PicoPOSSUM follows a strict clean architecture pattern across both the Rust backend and React frontend:

```
src-tauri/src/
├── domain/            Domain entities, request/response models, business rules
├── application/       Application services (ValidationService, SalesService, etc.)
├── infrastructure/    SQLite database connection, schema, OS paths, printer drivers
├── commands/          Tauri IPC command handlers (thin adapters)
└── errors/            Centralized AppError enum with user-facing messages
```

### Key Principles:
- **Separation of Concerns**: IPC commands in `commands/` must remain thin; all core business validation and calculation belongs in `application/`.
- **Validation**: Every payload must be validated in `application/validation_svc.rs` before database execution.
- **Maintainability**: No individual source file should exceed 500 lines. Break large components into submodules.
- **No Hardcoded Currency**: Never hardcode currency symbols like `₹` or `$`. Use `formatPaise` and `getCurrencySymbol` from `src/lib/formatters.js`.

---

## 5. Testing & Quality Checks

Before submitting a pull request, verify that all backend test suites pass and the frontend compiles cleanly:

```bash
# 1. Run all backend integration tests
cargo test --manifest-path src-tauri/Cargo.toml

# 2. Build the production web bundle
npm run build
```

---

## 6. Pull Request Process

1. Create a descriptive feature branch:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Keep commits atomic and use conventional commit messages (`feat: ...`, `fix: ...`, `docs: ...`, `refactor: ...`).
3. Ensure no local secrets, `.env` files, or absolute filesystem paths are introduced.
4. Open a pull request against the `main` branch with a summary of the changes and testing performed.
