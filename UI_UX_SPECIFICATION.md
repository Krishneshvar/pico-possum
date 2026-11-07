# PicoPOSSUM — UI/UX Architecture & Interface Specification

> **Document Purpose**: This document serves as the comprehensive design, structural, and interface specification of the **PicoPOSSUM** desktop application. It details the app's visual hierarchy, layout geometry, color system, typography, interactive components, keyboard shortcuts, and screen-by-screen breakdown. It is structured to provide high-fidelity context for brainstorming, evaluating UI/UX refinements, and guiding frontend enhancements.

---

## 1. Application Architecture & Tech Stack

PicoPOSSUM is a high-performance, single-window desktop Point of Sale (POS) and inventory ERP engineered for retail environments.

- **Desktop Shell**: [Tauri v2](https://v2.tauri.app/) (Rust native process interfacing with platform Webview)
- **Frontend Core**: React 18, Vite 8, TanStack Query (React Query v5) for reactive server state management, and Zustand for transient POS carts and layout stores.
- **Styling**: Tailwind CSS v4 with custom CSS layers, glassmorphism panel helpers, and JetBrains Mono monospace numeric formatting.
- **Iconography**: Lucide React (standardized `14px`–`18px` stroke icons).
- **Workspace Model**: Browser-style multi-tab workspace allowing parallel workflows (e.g., POS checkout, product catalog editing, sales reports, and customer management running simultaneously without losing unsaved state).

---

## 2. Visual Design System & Color Palette

The interface implements a **Dark-Mode-First, High-Density Retail Ergonomics** design language. The dark palette reduces eye strain during long cash register shifts and provides sharp visual contrast against vibrant status colors and numeric data.

### 2.1 Theme Palette & Semantic Tokens

| Token Role | Tailwind Class | Hex / RGBA Value | Usage Context |
| :--- | :--- | :--- | :--- |
| **App Canvas / Deep Background** | `bg-slate-950` | `#020617` | Window base canvas, table backgrounds, nested inset inputs |
| **Surface / Navigation Shell** | `bg-slate-900` | `#0f172a` | Top navigation bar, table headers, tab strips, primary modal shells |
| **Panel / Subtle Surface** | `bg-slate-900/60` | `rgba(15, 23, 42, 0.6)` | Search bars, filter toolbars, footer summary bars |
| **Card / Glass Container** | `.glass-card` | `rgba(30, 41, 59, 0.5)` | KPI metric cards, analytical widgets, cart sidebars |
| **Card Backdrop Blur** | `backdrop-blur-md` | `blur(8px)`–`blur(12px)` | Glassmorphism floating cards, modal overlays |
| **Primary Borders & Rules** | `border-slate-800` | `#1e293b` | Structural panel dividers, table row borders, card borders |
| **Subtle Borders** | `border-slate-800/80` | `rgba(30, 41, 59, 0.8)` | Input borders, tab separators, nested row dividers |
| **Highlight Borders** | `border-slate-700` | `#334155` | Focused buttons, active hover states |

### 2.2 Text & Typography Tokens

| Token Role | Tailwind Class | Font Family | Usage Context |
| :--- | :--- | :--- | :--- |
| **Primary Headings & Titles** | `text-slate-100` | Inter / Sans | Window headers, modal titles, active tab text |
| **Standard Body Text** | `text-slate-200`, `text-slate-300` | Inter / Sans | Table cell contents, button labels, descriptions |
| **Muted / Secondary Labels** | `text-slate-400`, `text-slate-500` | Inter / Sans | Field labels, timestamps, table column headers, helper text |
| **Financial / Numeric Data** | `font-mono text-slate-100` | JetBrains Mono | Prices, paise values, SKU numbers, barcode text, quantities |

### 2.3 Semantic Accent Colors

| Role | Color Family | Tailwind Classes | UI Application |
| :--- | :--- | :--- | :--- |
| **Primary Brand / Success** | Emerald | `text-emerald-400`, `bg-emerald-500` | Active bill tabs, completed checkouts, profit metrics, primary action buttons, cash badges |
| **Secondary / Informational** | Cyan / Sky | `text-cyan-400`, `bg-cyan-500` | UPI payments, search focus rings, average bill metrics, QR indicators |
| **Transactional / Neutral Action** | Blue / Indigo | `text-blue-400`, `bg-blue-500` | Card payments, manual stock adjustments, customer order history |
| **Warning / Discounts** | Amber | `text-amber-400`, `bg-amber-500` | Discounts given, low stock warnings, stock return badges, system alerts |
| **Danger / Cancellation / Out of Stock** | Rose | `text-rose-400`, `bg-rose-500` | Cancelled sales, deleted items, out-of-stock badges, destructive modal confirmations |
| **Analytical Comparison** | Violet / Pink | `text-violet-400`, `text-pink-400` | Multi-year comparison curves, legacy migration tags |

---

## 3. Global App Shell & Layout Geometry

The application layout is structured as a vertical stack occupying 100% of the viewport width and height (`h-screen w-screen overflow-hidden flex flex-col`):

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│ Top Navigation Bar (h-11 / 44px) — Brand | Dropdowns | Direct Links | User & Lock  │
├────────────────────────────────────────────────────────────────────────────────────┤
│ Tab Strip (h-9 / 36px) — Draggable Browser Tabs | Close (Ctrl+W) | Overflow Scroll │
├────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                    │
│ Main Workspace (flex-1 / overflow-hidden)                                          │
│ [Active Tab View occupies full viewport; inactive tabs remain mounted in DOM]       │
│                                                                                    │
└────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Top Navigation Bar (`NavBar.jsx`)
- **Height & Layout**: Fixed height of `44px` (`h-11`), flex container aligned horizontally (`items-center px-3 gap-2 bg-slate-900 border-b border-slate-800/80`).
- **Left Section (Brand)**:
  - App Icon (`w-7 h-7 rounded-lg`, shell mascot logo).
  - Brand Title: `PicoPOSSUM` (`font-bold text-slate-100 text-sm tracking-tight`).
  - Vertical rule separator (`w-px h-5 bg-slate-800 mx-1`).
- **Center Section (Menu Navigation)**:
  - **Inventory Menu** (Dropdown with `Package` icon):
    - *Products* (`viewId: 'products'`)
    - *Categories* (`viewId: 'categories'`)
    - *Stock Activity* (`viewId: 'stock-ledger'`)
  - **Commercial Menu** (Dropdown with `ShoppingCart` icon):
    - *Point of Sale* (`viewId: 'pos'`)
    - *Bill History* (`viewId: 'bills'`)
    - *Customers* (`viewId: 'customers'`)
  - **Insights Menu** (Dropdown with `BarChart3` icon):
    - *Sales Reports* (`viewId: 'sales-reports'`)
    - *Sales Analytics* (`viewId: 'reports'`)
    - *Product Flow* (`viewId: 'product-flow'`)
    - *Audit Log* (`viewId: 'audit-log'`)
  - **Settings** (Direct Action button with `Settings` icon):
    - Opens or focuses `SettingsView` (`viewId: 'settings'`).
- **Right Section (User Session & Lock Terminal)**:
  - Logged-in admin badge: `bg-slate-950/70 border border-slate-800 px-2.5 py-1 rounded-lg text-[11px]` featuring a pulsing emerald dot, user icon, full name, and `@username`.
  - **Lock Button**: Compact action button with `LogOut` icon that prompts a warning confirm dialog before clearing credentials and locking the terminal back to the `AuthScreen`.

### 3.2 Tab Strip (`TabBar.jsx`)
- **Height & Layout**: Height of `36px` (`h-9`), horizontal scrolling tab bar (`bg-slate-950 border-b border-slate-800/80`).
- **Tab Ergonomics**:
  - Displays each open workspace tab with its title and a close button (`X` icon).
  - **Active Tab Styling**: `bg-slate-900 text-slate-100 border-t-2 border-t-emerald-500 border-x border-slate-800 font-semibold shadow-inner`.
  - **Inactive Tab Styling**: `bg-slate-950 text-slate-400 hover:text-slate-200 hover:bg-slate-900/40 border-r border-slate-800/50`.
  - **Tab Drag & Drop Reordering**: Pointer-event drag system allowing intuitive rearrangement of open tabs.
  - **Keyboard Shortcut**: `Ctrl + W` immediately closes the currently active tab.
  - **Tab Preservation**: All views remain mounted in the DOM (`display: flex` for active tab, `display: none` for inactive tabs). Switching tabs never loses draft invoices, search queries, or scroll offsets.

### 3.3 Empty Workspace State
When all tabs are closed, a centered watermark view renders with the semi-transparent app icon (`opacity-20`), displaying "Nothing open yet" and instructions to open a view from the menu bar above.

---

## 4. Reusable Component System

### 4.1 Enterprise Data Table (`DataTable.jsx`)
The centerpiece of the inventory, commercial records, and audit screens is the custom `DataTable`:
- **Column Reordering**: Headers can be dragged horizontally using grip handles to reorganize columns.
- **Column Resizing**: Interactive edge drag handles allowing per-column pixel width resizing.
- **Header Sorting**: Tri-state sorting (Ascending `↑` → Descending `↓` → Unsorted).
- **Persistent State**: Column widths and ordering are automatically saved to `localStorage` under `pico-possum:table-state:<key>`.
- **Search & Filter Bar**: Embedded global text search across configurable keys, combined with multi-select dropdown pills and date range pickers.
- **Pagination**: Configurable rows per page (10, 25, 50, 100), current range indicator (e.g. `1–25 of 142`), and forward/back navigation buttons.
- **Row Selection**: Optional checkbox selection column with bulk action triggers.

### 4.2 Modal Dialogs & Action Confirmations (`ConfirmDialog.jsx`)
- **Visuals**: Full-screen backdrop blur overlay (`bg-slate-950/80 backdrop-blur-sm z-50`).
- **Card**: Centered container with `bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl max-w-md w-full`.
- **Semantic Tones**:
  - `info`: Blue icon badge.
  - `warning`: Amber icon badge (used for terminal locks and bill cancellations).
  - `danger`: Rose icon badge (used for customer and product deletions).
- **Keyboard Handling**: `Escape` cancels; `Enter` confirms.

### 4.3 Notification System (`ToastContainer.jsx`)
- **Position**: Floating in the top-right viewport corner (`fixed top-4 right-4 z-50 flex flex-col gap-2.5`).
- **Anatomy**: Sleek card (`bg-slate-900/95 border border-slate-700/80 rounded-xl p-3.5 shadow-2xl`) featuring an icon badge (Success, Error, Warning, Info), title, message, and an animated linear progress bar indicating auto-dismissal (typically 3500ms).

---

## 5. Screen-by-Screen Detailed Breakdown

### 5.1 Authentication Screen (`AuthScreen.jsx`)
- **Context & Purpose**: Displayed on application startup when no active session token exists, or on first run to initialize the master administrator credentials.
- **Layout**: Centered single-column login card (`max-w-md w-full`) surrounded by an ambient radial glow (`radial-gradient(circle, rgba(16,185,129,0.08) 0%, transparent 70%)`).
- **Components**:
  - Logo shell with animated border glow.
  - Header: "Welcome to PicoPOSSUM" / "Administrator Sign In".
  - Subtitle: Dynamic status indicator ("Terminal Locked" or "Initial First-Time Terminal Setup").
  - Form Fields:
    - Full Name (only during first-time setup)
    - Username (pre-filled with existing admin username on login)
    - Password (with show/hide eye toggle button, auto-focused on launch)
    - Confirm Password (only during setup)
  - Action Button: Large gradient button ("Sign In to Terminal" / "Initialize Administrator Account") with loading spinner.
  - Shake Animation: Card triggers a CSS shake animation on failed authentication attempts.

---

### 5.2 Point of Sale (`PosView.jsx` & `PosBillEditor.jsx`)
- **Context & Purpose**: The high-speed checkout terminal where cashiers build carts, apply discounts, scan barcodes, tender money, and print thermal receipts.
- **Top Bar (Multi-Bill Tab Switcher)**:
  - Allows maintaining **up to 5 concurrent open bills** in memory simultaneously.
  - Horizontal chip buttons: `Bill 1`, `Bill 2`, `Bill 3`, `Bill 4`, `Bill 5`.
  - Each chip displays an item count badge (e.g. `3` items). Active bill is highlighted in solid emerald.
  - Action to clear current cart or cycle via `Ctrl + N`.
- **Two-Column Split Layout**:
  - **Left Section (`flex-1`, Cart & Product Catalog)**:
    - **Top Search Bar**: Scan barcode, SKU, or search name (`Ctrl + K`). Displays a floating dropdown preview of matching products with live stock badges.
    - **Category Quick-Filter Chips**: Scrollable row of category pills (e.g., `All`, `Beverages`, `Snacks`, `Bakery`) to quickly filter the product grid.
    - **Itemized Cart Table**:
      - Headers: `#`, `Product Name`, `Quantity`, `Rate (₹)`, `MRP`, `Disc (₹)`, `Total (₹)`, `Actions`.
      - Quantity cell features `-` and `+` micro-buttons plus direct numeric input.
      - Rate and Line Discount are editable inline with live line-total recalculation.
      - Low-stock visual warning badges if quantity exceeds available inventory.
    - **Cart Bottom Summary**: Displays total cart line count, total units sold, and cart subtotal.
  - **Right Section (`w-96`, Customer & Financial Checkout Sidebar)**:
    - **Customer Selector**: Attach walk-in customer or search registered customers with phone numbers; quick button to open `AddCustomerModal`.
    - **Payment Method Buttons**: Grid of 4 selectable payment buttons: `Cash` (Banknote icon), `UPI / QR` (QrCode icon), `Card` (CreditCard icon), and `Other`.
    - **Discount Control**: Flat rupee discount input.
    - **Financial Breakdown Card**:
      - Subtotal (Paise formatted in Rupees)
      - Tax / GST breakdown
      - Discount deduction
      - **Grand Total**: Oversized high-contrast font (`text-2xl font-black text-emerald-400 font-mono`).
    - **Tendered & Change Calculator**:
      - Amount Tendered input with "Exact / Full" quick-fill button.
      - Change Due display: Automatically calculates return change in large bold font.
    - **Primary Checkout Button**: `Complete Sale [F12 / Ctrl+Enter]` button. Triggers sale processing, stock decrement, and opens the Receipt Modal.
- **Sale Completion & Receipt Modal**:
  - Confirmation screen displaying invoice number (e.g. `S261028-0001`), change to return, and print button for thermal printers (`ReceiptPrinter`).

---

### 5.3 Bill History (`BillsHistoryView.jsx`)
- **Context & Purpose**: Searchable ledger of all historical sales transactions, customer receipts, and cancellations.
- **Top Header & Toolbar**:
  - Title: "Bill History" with subtitle "Search and audit finalized sales transactions".
  - Export Options: Export current filtered view as **CSV** or **Excel (.xlsx)**.
  - Search & Status Filter: Filter by `PAID` or `CANCELLED`.
- **Main Table Columns**:
  - `Invoice #`: Monospace bill identifier (e.g. `S261028-0004`). Clicking invoice opens `BillDetailView`.
  - `Date & Time`: Formatted checkout timestamp.
  - `Customer`: Name and phone number (or "Walk-in Customer").
  - `Payment`: Payment method badge (Cash, UPI, Card, Other).
  - `Subtotal`, `Discount`, `Tax`, `Total Amount`: Right-aligned monospace currency.
  - `Status`: Emerald badge for `PAID`, Rose badge for `CANCELLED`.
  - `Actions`: Context menu button to *View Bill*, *Edit Bill*, *Reprint Receipt*, or *Cancel Sale*.
- **Bill Cancellation Flow**: Prompts cashier for a cancellation reason, marks sale as `CANCELLED`, logs an audit entry, and automatically returns all sold items back to stock inventory.

---

### 5.4 Bill Detail & POS-Style Editor (`BillDetailView.jsx`)
- **Context & Purpose**: Comprehensive inspection of an individual bill, with the ability to switch into full POS edit mode.
- **View Header**:
  - Title: `Bill #<Invoice_Number>`.
  - Status Badge: `PAID` or `CANCELLED`.
  - Action Controls: Toggle between **View Mode** and **Edit Mode** (`Pencil` icon), Reprint Receipt, and Back button.
- **Read Mode (2-Column Java-Aligned Layout)**:
  - **Left Column (`flex-1`)**: Itemized sale items table showing Product Name, SKU, Quantity, Unit Rate, MRP, Discount, and Net Line Total.
  - **Right Column (`w-80` to `w-96` Sidebar)**:
    - Metadata Card: Invoice number, cashier name, customer phone, creation timestamp, last updated timestamp.
    - Financial Summary Card: Gross Subtotal, Item Discounts, Bill Discount, Net Tax, and Final Total.
    - Payment Details: Method used (Cash/UPI/Card), amount tendered, and change returned.
    - Notes: Customer or cashier remarks.
- **Edit Mode (`PosBillEditor.jsx`)**:
  - Transforms the entire screen into the identical, familiar POS checkout interface with pre-filled items, customer, discount, and tendered amount.
  - Cashiers can add new products, adjust quantities, change prices, and click "Save & Update Bill", which automatically reconciles inventory stock differences and updates the invoice.

---

### 5.5 Products Catalog (`ProductsView.jsx`)
- **Context & Purpose**: Product inventory master, SKU management, pricing, and stock monitoring.
- **Top Metric Cards**:
  - *Total Products*: Count of active catalog items.
  - *Low Stock Alert*: Count of items currently at or below minimum threshold (clicking filters table).
  - *Total Catalog Value*: Monospace rupee sum of all held stock at retail price.
- **Toolbar & Actions**:
  - Create Product (`+ New Product` button).
  - Import Products via CSV (`ImportCsvButton`).
  - Search and Category filter dropdowns.
- **Product Table Columns**:
  - `SKU`: Monospace product code with auto-generation support.
  - `Product Name`: Primary title with category subtitle.
  - `Category`: Category pill badge.
  - `Cost Price`: Wholesale cost.
  - `MRP / Selling Price`: Retail price.
  - `Tax Rate`: GST percentage (e.g. `18.0%`).
  - `Stock`: Units on hand with color coding (Emerald for healthy, Amber for low, Rose for zero/negative).
  - `Status`: `active`, `inactive`, or `discontinued`.
  - `Actions`: Edit product modal, Adjust Stock dialog, Delete product.
- **Product Modal (`Create / Edit`)**:
  - Clean two-column modal form with inputs for Name, SKU (with next-auto preview), Category select, MRP, Cost Price, Tax Rate, Stock Alert, and Status.
- **Stock Adjustment Modal**:
  - Fast dialog allowing `RESTOCK`, `PURCHASE`, `RETURN`, or `MANUAL_ADJUSTMENT` with delta quantity and audit notes.

---

### 5.6 Categories Hierarchy (`CategoriesView.jsx`)
- **Context & Purpose**: Organization of the product catalog into nested hierarchical groups.
- **Resizable Split Partition**:
  - **Left Pane (Category Tree View)**:
    - Interactive nested tree browser with expandable/collapsible folders (`FolderOpen` / `Folder`).
    - Node action triggers to add subcategories, edit category names, or delete.
    - Displays product count badges per category node.
  - **Divider**: Draggable partition handle allowing users to resize the tree vs table split.
  - **Right Pane (Category Data Table)**:
    - Shows category records with ID, Name, Parent Category, Product Count, and Actions.
    - Toolbar includes `+ Add Category` and CSV bulk import.
- **Create/Edit Category Modal**:
  - Simple modal capturing Category Name and Parent Category dropdown.

---

### 5.7 Stock Activity / Stock Ledger (`StockLedgerView.jsx`)
- **Context & Purpose**: Complete FIFO inventory audit log tracking every unit movement in the system.
- **Filter Bar**: Date range filter (From / To) with quick presets and refresh trigger.
- **Data Table Columns**:
  - `Date & Time`: Timestamp of movement.
  - `SKU`: Product code.
  - `Product`: Item name.
  - `Type`: Movement badge (`SALE` in rose, `RESTOCK` in emerald, `PURCHASE` in emerald, `RETURN` in amber, `MANUAL_ADJUSTMENT` in blue).
  - `Qty Change`: Positive or negative quantity delta (e.g. `+50` or `-2`).
  - `Balance`: Resulting stock level after transaction.
  - `Reference`: Clickable invoice ID link (navigates directly to the bill).
  - `Notes / Reason`: Audit remark.

---

### 5.8 Customers Management (`CustomersView.jsx`)
- **Context & Purpose**: Customer relationship records, contact details, and historical purchase statistics.
- **Main Layout**:
  - **Left Section (Customer Directory Table)**:
    - Searchable table: Customer Name, Phone Number, Address, Total Bills, Total Spent.
    - Actions: Edit customer, Delete customer, and "View History" slide-out button.
  - **Right Section (Customer Profile & Purchase History Drawer)**:
    - Opens when a customer row is clicked.
    - Profile Header: Customer initials avatar, name, phone, address.
    - Summary Cards: Total Orders, Lifetime Spend, Average Order Value, Last Order Date.
    - Order History Table: List of all previous bills issued to this customer with direct view links.

---

### 5.9 Sales Reports (`SalesReportsView.jsx`)
- **Context & Purpose**: Tabular periodic financial summaries for accounting and tax reconciliation.
- **Report Controls**:
  - **Interval Selector Tabs**: `Daily`, `Monthly`, `Yearly`.
  - **Date Range Picker**: From and To date inputs with quick range buttons (`Today`, `This Month`, `All Time`).
  - **Amount Threshold Filters**: Filter rows by minimum and maximum amounts across gross, net, cash, upi, or card sales.
  - **Column Visibility Selector**: Multi-select dropdown allowing cashiers/accountants to toggle individual table columns on/off.
  - **Export Buttons**: Export tailored report data to **CSV** or formatted **Excel (.xlsx)**.
- **Table Columns**:
  - Period (`YYYY-MM-DD` for daily, `YYYY-MM` for monthly, `YYYY` for yearly)
  - Total Transactions count
  - Cash Sales, UPI Sales, Card Sales, Other Sales
  - Discounts Given
  - Gross Sales
  - Net Sales
- **Sticky Totals Bar**: Footer pinned at the bottom displaying cumulative sums for every active column in the reporting period.

---

### 5.10 Sales Analytics (`ReportsView.jsx` & `MultiYearComparisonChart.jsx`)
- **Context & Purpose**: High-level visual dashboard featuring financial KPIs, multi-year performance comparisons, channel distribution, and top product/category rankings.
- **Top Filter Toolbar**:
  - Date Range pickers with `Today`, `This Month`, `All Time`.
  - **Payment Method Filter Dropdown**: `All Payment Methods`, `Cash`, `UPI / QR`, `Card`, `Other`.
- **Dynamic Filter-Based View**:
  - When a specific payment method is chosen (e.g. `Cash`), the "Revenue by Payment Method" breakdown cards are automatically hidden to remove redundancy, and an active filter pill is shown.
- **Top Metric Cards**:
  - Total Revenue (paise formatted).
  - Total Bills Issued.
  - Average Bill Value.
  - Total Discounts Given.
- **Multi-Year Performance Comparison Chart (`MultiYearComparisonChart.jsx`)**:
  - Interactive SVG multi-line visualization comparing month-by-month performance across years.
  - Controls: Compare back selector (`Last 2 Years`, `Last 3 Years`, `Last 5 Years`).
  - Interactive Series Legend: Toggle individual years on/off by clicking year chips.
  - Curated distinct color curves (Emerald `#10b981`, Cyan `#06b6d4`, Violet `#8b5cf6`, Amber `#f59e0b`, Pink `#ec4899`).
  - Interactive Tooltip: Hovering over points displays exact Year, Month, Total Sales (₹), and transaction volume with vertical crosshair tracking.
- **Payment Method Distribution (when `ALL` is selected)**:
  - Visual stacked percentage distribution bar showing proportion of Cash vs UPI vs Card vs Other.
  - Breakdown cards for Cash, UPI / QR, and Card revenue.
- **Side-by-Side Category & Product Rankings (2-Column Grid)**:
  - **Top Selling Categories Card**: Table showing `#`, Category Name, Distinct Products Sold, Total Units Sold, Total Revenue, with a bottom Totals Bar.
  - **Top Selling Products Card**: Table showing `#`, Product Name & SKU, Qty Sold, Total Revenue, with a bottom Totals Bar.

---

### 5.11 Product Flow View (`ProductFlowView.jsx`)
- **Context & Purpose**: Deep-dive item velocity ledger tracking every single sale line-item for a specific product over time.
- **Controls**: Product selector autocomplete dropdown and reporting date range.
- **Table Columns**: Timestamp, Invoice Number (clickable), Product SKU, Customer Name, Payment Method, Quantity Sold, Unit Price, Total Revenue.

---

### 5.12 System Audit Log (`AuditLogView.jsx`)
- **Context & Purpose**: Immutable security audit trail logging every administrative, inventory, and sales event.
- **Features**: Event type multi-select filter (`SALE_CREATED`, `SALE_CANCELLED`, `PRODUCT_CREATED`, `PRODUCT_UPDATED`, `PRODUCT_DELETED`, `STOCK_ADJUSTMENT`, `CUSTOMER_CREATED`, `LEGACY_MIGRATION`).
- **Table Columns**: Timestamp, Event Action badge, Entity Module, Entity ID, Description.
- **Detail Drawer**: Clicking any audit row opens a slide-over panel displaying formatted JSON payloads and metadata for forensics.

---

### 5.13 Settings & Configuration (`SettingsView.jsx`)
- **Context & Purpose**: Global configuration center divided into 5 dedicated tabs:
  1. **General Settings**:
     - Store Name, Store Phone, Store Address, GSTIN number.
     - Currency preferences (Symbol `₹`, Code `INR`).
     - Date Format (`DD-MM-YYYY` / `YYYY-MM-DD`) and Time Format (`12-hour` / `24-hour`).
     - Inventory Policies: Auto-generate numeric SKUs toggle, Allow negative stock checkout toggle.
     - Security: Change master administrator password form.
  2. **Bill Settings (Thermal Receipt Designer)**:
     - Interactive drag-to-reorder list of receipt sections: Store Header, Bill Metadata, Items Table, Tax Summary, Total Summary, Footer Note.
     - Toggle visibility, font size (`small`, `medium`, `large`), and alignment (`left`, `center`, `right`) per section.
     - Multi-line textarea for receipt footer note (e.g. return policy, thank you message).
     - Live interactive preview of thermal receipt rendered side-by-side.
  3. **Hardware & Printing**:
     - Auto-detection of installed OS thermal printers.
     - Default printer selector.
     - "Send Test Print" trigger.
  4. **Backup & Restore**:
     - Automatic scheduled backup status and backup folder location.
     - "Create Manual Backup Now" button.
     - Table of existing database backups with date, file size, and 1-click restore.
     - "Restore from Backup File" drag-and-drop file upload.
     - "Open Backups Folder" in OS native file explorer.
  5. **Migration & CSV Imports**:
     - SQLite database migrator for importing legacy Java PicoPOSSUM `.db` files.
     - Dedicated CSV import cards with progress indicators for Customers, Categories, Products, and Historical Bills.

---

## 6. Layout Principles, Alignments & UX Heuristics

### 6.1 Viewport Ergonomics & Scroll Boundaries
- **Fixed vs. Scrollable**: The application window never triggers a full-window scrollbar. The top navbar and tab bar remain completely static. Each open tab view manages its own internal overflow.
- **Table Viewports**: Table headers (`<thead>`) are pinned (`sticky top-0 bg-slate-900 z-10`), while table bodies scroll independently. Footers containing summary sums are anchored at the bottom of the table card.
- **Split Views**: Split views (Categories and POS) utilize full-height flex layouts (`flex-1 flex overflow-hidden`), ensuring the left catalog and right checkout sidebar scroll independently.

### 6.2 Data Alignment Standards
- **Monospace Financials**: All currency values (`₹`), paise calculations, invoice numbers, and SKU codes strictly utilize monospaced font family (`font-mono`) and right-alignment (`text-right`) to ensure decimal points align vertically down columns for fast optical scanning.
- **Quantities & Counts**: Quantities and item counts are centered (`text-center font-mono font-bold`).
- **Entity Names**: Customer names, product titles, and category labels are left-aligned (`text-left font-semibold text-slate-200`).
- **Badges & Statuses**: Status tags (Payment method, active/inactive, movement types) use pill borders with centered text (`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold`).

### 6.3 Keyboard-First POS Accelerated Workflow
The POS interface is optimized for cashier speed without requiring mouse interaction:
- `Ctrl + K`: Instantly focus barcode/product search field.
- `Enter` (in search): Add first matching or exact-match product directly to cart.
- `Ctrl + N`: Jump to next empty bill tab.
- `F12` or `Ctrl + Enter`: Instantly trigger sale checkout and tender payment.
- `Ctrl + W`: Close active workspace tab.
- `Esc`: Close open modal dialogs, search previews, or clear focus.

---

## 7. Strategic Brainstorming Angles for UI/UX Evolution

When collaborating with AI models to improve or evolve PicoPOSSUM's interface, the following areas represent prime opportunities for high-impact innovation:

1. **Touchscreen POS Mode**: Designing an alternate touch-friendly layout toggle for POS with larger button tap targets (`48px` minimum), a visual on-screen numpad for tendering cash, and a visual grid of top product image cards.
2. **Keyboard Navigation Matrix**: Implementing spatial arrow-key navigation in the itemized cart table (allowing cashiers to use Up/Down arrows to select rows and Left/Right to bump quantities).
3. **Receipt Customization Canvas**: Enhancing the thermal receipt designer into a drag-and-drop visual canvas with custom logo image uploading.
4. **Offline / Network Health Indicator**: Adding a subtle status pill in the top navbar showing database latency, background sync health, or printer connectivity.
5. **Sound & Haptic Feedback**: Optional subtle audio chirps on barcode scan success, checkout complete, or out-of-stock warning.
6. **Command Palette (`Ctrl + P`)**: A global fuzzy-search spotlight allowing instant jumping between bills, customers, settings, or products from anywhere in the application.
