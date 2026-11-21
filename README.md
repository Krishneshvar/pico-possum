# PicoPOSSUM

A local-first, offline desktop **point-of-sale and retail management system** for small shops. PicoPOSSUM combines a fast checkout terminal with inventory control, customer management, sales analytics, receipt design, and data migration tools in a single cross-platform application.

Built with **Tauri 2**, **React 19**, **Rust**, and **SQLite**, all data stays on the machine — no internet connection or external server is required.

---

## Features

### Point of Sale
- Fast product search by name or SKU, with keyboard-driven checkout.
- Up to **9 simultaneous open bills**, persisted automatically to SQLite so a crashed or closed session can be restored.
- Per-line quantity, unit price, and discount editing; fixed or percentage bill discounts.
- Customer attachment, quick customer creation, and walk-in sales.
- Payment methods: **Cash, Card, UPI, and Gift Card**.
- Full or partial payments with tendered amount and change calculation.
- Configurable handling of sales that exceed available stock.
- Thermal receipt preview and printing for **58 mm** and **80 mm** paper.
- Invoice numbers generated per payment method and calendar year (e.g. `S25CH0000123`).

### Inventory & Catalog
- Product catalog with SKU, category, MRP, cost price, tax rate, stock level, and low-stock threshold.
- Optional automatic numeric SKU generation.
- Hierarchical product categories.
- Stock ledger recording every movement (purchases, sales, returns, corrections, bill adjustments) with references and notes.
- Low-stock visibility and stock adjustment workflow.
- CSV/Excel export and import for products, categories, customers, and bill history, plus report exports.

### Sales & Customers
- Searchable bill history with detailed invoice view.
- Amend existing bills, reprint receipts, and cancel sales with automatic inventory restoration.
- Cancel/return reasons recorded in the audit trail.
- Customer directory with contact details and per-customer purchase history.
- Export and import of customer records.

### Reports & Insights
- Financial sales reports filtered by today, daily, monthly, or yearly periods.
- Gross/net sales, payment-method breakdowns, discounts, and bill counts.
- Top-selling products and categories.
- Multi-year sales comparison charts.
- Product flow view linking every sale and return to its product.
- Audit log of sales, inventory changes, imports, authentication events, and migrations.

### Settings & Administration
- Store profile, contact details, GSTIN, currency, and date/time formats.
- Configurable receipt designer with reorderable sections, alignment, font sizes, and optional tax display.
- System printer detection and a test-print action through the OS print spooler where supported.
- Manual backup creation, restore from file, and open-backup-folder shortcut.
- Legacy Java POS database detection and one-click migration.
- CSV/Excel import wizards with column aliases and row-level error reporting.
- Light and dark themes.
- Security settings for the administrator account and terminal lock.

### Security
- First-run administrator registration; only one administrator account can be created.
- Passwords hashed with **bcrypt**.
- In-memory session management with session tokens.
- Login rate limiting after repeated failed attempts.
- Terminal lock requiring the administrator password to re-enter.
- Audit logging for authentication, sales, inventory, imports, and restores.

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| Desktop shell | [Tauri 2](https://tauri.app/) |
| Frontend | React 19, Vite 8, Tailwind CSS 4 |
| State & data fetching | Zustand, TanStack Query |
| Icons | Lucide React |
| Spreadsheet I/O | SheetJS (`xlsx`) |
| Backend | Rust 2021, Tokio |
| Database | SQLite via `sqlx` (WAL mode) |
| Authentication | `bcrypt` |
| IDs & time | `uuid`, `chrono` |
| Errors | `thiserror` |

---

## Architecture

PicoPOSSUM follows a layered Rust backend behind Tauri IPC commands:

```
src/                          React frontend
├── components/               Shared UI, layout, receipt, and common controls
├── features/                 Feature views (POS, inventory, reports, settings, auth)
├── stores/                   Zustand stores (POS drafts, theme, toasts, workspace)
└── lib/                      Tauri API wrapper, formatters, exports, sorting

src-tauri/src/                Rust backend
├── domain/                   Data models and business rules
├── application/              Services: auth, sales, inventory, backup, CSV, migration
├── infrastructure/           SQLite pool/schema and application paths
├── commands/                 Tauri `invoke` command handlers
└── errors/                   Typed application errors
```

- **Money is stored as integer cents / minor units** (e.g. `1/100` of a currency unit) throughout the database and API to avoid floating-point rounding errors.
- The SQLite schema is created/upgraded automatically at startup and includes products, categories, customers, sales, sale items, stock movements, audit logs, settings, invoice sequences, and open POS bills.
- The frontend communicates with Rust exclusively through typed Tauri commands exposed in `src/lib/tauri.js`.
- On graceful exit the app checkpoints the SQLite WAL and creates an exit backup before closing.

---

## Getting Started

### Prerequisites

- **Node.js** 20.19 or newer (Vite 8 requires `^20.19.0 || >=22.12.0`) and npm
- **Rust** stable toolchain (`rustup`)
- **Tauri system dependencies** for your OS — see [Tauri prerequisites](https://v2.tauri.app/start/prerequisites/).

On Debian/Ubuntu, the Tauri packages are typically:

```bash
sudo apt update
sudo apt install libwebkit2gtk-4.1-dev build-essential curl wget file \
  libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev
```

### Install

```bash
npm install
```

### Run in development

```bash
npm run tauri dev
```

This starts Vite on `http://localhost:1420` and launches the native Tauri window with hot reload.

### Build for production

```bash
npm run tauri build
```

Installers and bundles are written to `src-tauri/target/release/bundle/`.

### Frontend-only development

```bash
npm run dev
```

Useful for UI work, but Tauri commands will not be available without the desktop shell.

### npm scripts

| Command | Description |
| --- | --- |
| `npm run dev` | Start the Vite frontend dev server |
| `npm run build` | Build the frontend into `dist/` |
| `npm run preview` | Preview the production frontend build |
| `npm run tauri` | Run the Tauri CLI (e.g. `npm run tauri dev`) |

### First run

1. Launch the application and create the administrator account (username and password).
2. Open **Settings → General** to set the store name, address, and contact details.
3. Add categories and products under **Inventory**, or import them from CSV/Excel.
4. Open **Sales → Point of Sale** and start billing.

---

## Data Storage

PicoPOSSUM stores its database and backups in the OS application-data directory:

| OS | Location |
| --- | --- |
| Linux | `~/.local/share/pico-possum-tauri/` |
| macOS | `~/Library/Application Support/pico-possum-tauri/` |
| Windows | `%APPDATA%\pico-possum-tauri\` |

Inside that directory:

```
pico-possum-v2.db        Live SQLite database
backups/                 Manual, exit, and pre-restore backups
```

Backup files are named by type and timestamp, for example:

- `pico-possum-manual-backup-YYYY-MM-DD_HH-MM-SS.db`
- `pico-possum-exit-backup-YYYY-MM-DD_HH-MM-SS.db`
- `pico-possum-pre-restore-*.db`

Exit backups are pruned to the five most recent copies. Before any restore, a safety backup of the current database is created automatically.

---

## Importing Data & Migrating

### CSV / Excel import

Open **Settings → Imports & Migration**. Files can be `.csv`, `.xlsx`, or `.xls`. The importer accepts the app's own exported files as well as common legacy column names.

| Entity | Required columns |
| --- | --- |
| Categories | Category/Division Name |
| Products | Product Name, Product Code/SKU, MRP |
| Customers | Customer Name |
| Bills | Invoice/Bill Number, Date, Net Total/Net Amount |

Optional columns include category, cost price, opening stock, tax rate, low-stock alert, customer phone/address, payment method, subtotal, discount, tax, status, and notes. Invalid rows are skipped and reported with row numbers.

### Legacy Java POS migration

PicoPOSSUM can detect a previous Java POS installation at:

```
<OS data directory>/pico-possum/database/pico-possum.db
```

If detected, **Settings → Imports & Migration** offers a one-click import of users, categories, customers, products, sales, sale items, stock movements, and settings. Old monetary values are converted to cents automatically, and the migration is recorded in the audit log.

A legacy database can also be restored directly from **Settings → Backup & Restore**; the app detects and converts it during the restore process.

---

## Keyboard Shortcuts

### Checkout

| Shortcut | Action |
| --- | --- |
| `Ctrl/Cmd + K` | Focus product search |
| `Ctrl/Cmd + 1…9` | Switch to open bill 1–9 |
| `Ctrl/Cmd + N` | Jump to the next empty bill |
| `F12` or `Ctrl/Cmd + Enter` | Complete the current sale |
| `Escape` | Close dialogs or leave search |

### Line-item grid

| Shortcut | Action |
| --- | --- |
| Arrow keys | Move between editable cells |
| `Tab` / `Shift + Tab` | Move forward/backward through the grid |
| `+` / `-` | Increase/decrease quantity |
| `Delete` | Remove the selected line |
| `Enter` | Edit or confirm the selected cell |

---

## Testing

Backend tests use Rust's built-in test harness and in-memory or temporary SQLite databases:

```bash
cd src-tauri
cargo test
```

Coverage includes:

- Authentication lifecycle, first-admin lockdown, login/logout, and password updates.
- Full POS flow: catalog creation, stock, checkout, bill updates, and cancellation.
- CSV imports for categories, products, customers, and bills.
- Legacy database migration.
- Backup/restore lifecycle and settings persistence.
- Automatic SKU generation and stock adjustments.

Frontend checks:

```bash
npm run build
```

There is currently no frontend unit-test suite.

---

## Project Structure

```
pico-possum/
├── index.html
├── package.json
├── vite.config.js
├── src/                       React application
│   ├── components/
│   ├── features/
│   ├── stores/
│   ├── lib/
│   └── assets/
└── src-tauri/                 Tauri + Rust backend
    ├── Cargo.toml
    ├── tauri.conf.json
    ├── capabilities/
    ├── icons/
    ├── src/
    │   ├── domain/
    │   ├── application/
    │   ├── infrastructure/
    │   ├── commands/
    │   └── errors/
    └── tests/
```

---

## Contributing & Security

- Please read our [Contributing Guide](CONTRIBUTING.md) for details on our code of conduct, development setup, and pull request procedures.
- For private vulnerability disclosures and security policies, please review our [Security Policy](SECURITY.md).

---

## License & Third-Party Notices

Licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full text.

Third-party open-source components, fonts, and assets used in this application are listed with their respective licenses in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
