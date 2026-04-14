#  POSSUM

> **P**oint **O**f **S**ale **S**olution for **U**nified **M**anagement

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub release](https://img.shields.io/github/v/release/Krishneshvar/possum)](https://github.com/Krishneshvar/possum/releases)
[![Build Status](https://img.shields.io/badge/Build-Gradle-blue.svg)](https://gradle.org/)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![JavaFX](https://img.shields.io/badge/UI-JavaFX-green.svg)](https://openjfx.io/)

**POSSUM** is a sleek, standalone desktop Point of Sale (POS) application designed for small to medium-sized businesses. Built with high-performance Java 21 and JavaFX, it provides a robust, offline-first solution for managing sales, inventory, customers, and financial analytics in a single unified interface.

---

## 📥 Download

You can find the latest stable releases of POSSUM on the **[GitHub Releases](https://github.com/Krishneshvar/possum/releases)** page.

- **Windows:** Download the `.msi` or `.exe` installer.
- **Other Platforms:** Use the portable JAR version (requires Java 21).

---

## ✨ Key Features

### 🛒 Sales & Checkout
- **Intuitive POS Terminal:** Fast product searching and checkout.
- **Draft Management:** Save and resume sales transactions easily.
- **Flexible Payments:** Support for multiple payment methods and custom payment policies.
- **Returns & Refunds:** Streamlined return processing with inventory auto-adjustment.

### 📦 Inventory & Procurement
- **Product Variants:** Manage complex products with sizes, colors, and multiple attributes.
- **Stock Tracking:** Real-time inventory monitoring with low-stock alerts.
- **Supplier Management:** Track vendors and manage purchase orders directly.
- **Inventory Lots:** Track product flows and stock movements with detailed audit trails.

### 📊 Insights & Reporting
- **Interactive Dashboard:** High-level overview of daily performance.
- **Deep Analytics:** Detailed reports on sales trends, product performance, and tax collections.
- **Export Ready:** Generate and export reports into industry-standard formats (Excel/CSV).

### 🛡️ Security & Reliability
- **Automated Backups:** Integrated daily database backups to prevent data loss.
- **Self-Healing:** Built-in health checks and database repair tools (Vacuum/Reindex).
- **Access Control:** Role-based permissions and secure password hashing (BCrypt).
- **Audit Logs:** Complete record of all critical system actions.

### ⚖️ Tax Management
- **Custom Tax Profiles:** Highly configurable tax engines to comply with local regulations.
- **Tax Exemptions:** Support for customer-based or product-based tax exemptions.

---

## 🚀 Getting Started

### Prerequisites
- **Java JDK 21+** (Download from [Adoptium](https://adoptium.net/))
- **SQLite** (Included as embedded database)

### Installation (Development)
1. **Clone the repository:**
   ```bash
   git clone https://github.com/Krishneshvar/possum.git
   cd possum
   ```

2. **Build the project:**
   Using the Gradle wrapper:
   ```bash
   # Windows
   gradlew.bat shadowJar

   # Linux/macOS
   ./gradlew shadowJar
   ```

3. **Run the application:**
   ```bash
   java -jar build/libs/possum-all.jar
   ```

### Creating a Standalone Installer
POSSUM use `jpackage` to create native installers (.msi for Windows, etc.):
```bash
gradlew createInstaller
```
The installer will be available in `build/installer`.

---

## 🛠️ Tech Stack

- **Language:** [Java 21](https://openjdk.org/projects/jdk/21/) (LTS)
- **UI Framework:** [JavaFX 21](https://openjfx.io/)
- **Database:** [SQLite](https://www.sqlite.org/) with [Flyway](https://flywaydb.org/) Migrations
- **ORM/Persistence:** JDBC with [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pooling
- **Icons:** [Ikonli](https://kordamp.org/ikonli/) (Boxicons & FontAwesome)
- **Reporting:** [Apache POI](https://poi.apache.org/)
- **Build System:** [Gradle](https://gradle.org/)

---

## 🏗️ Architecture

POSSUM follows a **Clean Architecture** approach to ensure maintainability and testability:
- **`com.possum.domain`**: Core entities, value objects, and business rules.
- **`com.possum.application`**: Use cases and service orchestration.
- **`com.possum.infrastructure`**: External concerns (Persistence, Serialization, Backups, Security).
- **`com.possum.ui`**: JavaFX controllers, views (FXML), and UI logic.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---

<p align="center">Made with ❤️ for independent business owners.</p>
