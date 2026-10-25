use std::collections::HashMap;
use chrono::{NaiveDate, NaiveDateTime, NaiveTime};
use sqlx::SqlitePool;
use uuid::Uuid;

use crate::domain::models::CsvImportResult;
use crate::errors::AppError;

pub struct CsvImportService;

impl CsvImportService {
    // ------------------------------------------------------------------------
    // Categories Import
    // ------------------------------------------------------------------------
    pub async fn import_categories(
        pool: &SqlitePool,
        csv_content: &str,
    ) -> Result<CsvImportResult, AppError> {
        let rows = parse_csv(csv_content);
        if rows.is_empty() {
            return Err(AppError::Validation("CSV file is empty".to_string()));
        }

        let header_idx = find_header_row_index(&rows, &["Division Code", "Division Name"])
            .or_else(|| find_header_row_index(&rows, &["Category Name"]))
            .or_else(|| find_header_row_index(&rows, &["Name"]))
            .ok_or_else(|| {
                AppError::Validation(
                    "Could not find valid category header row. Required: 'Division Code' and 'Division Name' (or 'Category Name')."
                        .to_string(),
                )
            })?;

        let headers = build_header_index(&rows[header_idx]);
        let mut tx = pool.begin().await?;

        let mut imported = 0usize;
        let mut skipped = 0usize;
        let mut errors = Vec::new();
        let total_rows = rows.len().saturating_sub(header_idx + 1);

        for (row_offset, row) in rows.iter().skip(header_idx + 1).enumerate() {
            let row_num = header_idx + 2 + row_offset;
            if is_row_empty(row) {
                continue;
            }

            let division_code = get_value(row, &headers, &["Division Code", "Code", "ID"])
                .and_then(|v| parse_integer(&v));
            let name = get_value(row, &headers, &["Division Name", "Category Name", "Name"]);

            let name = match name {
                Some(n) if !n.trim().is_empty() => n.trim().to_string(),
                _ => {
                    skipped += 1;
                    errors.push(format!("Row {}: Missing category name", row_num));
                    continue;
                }
            };

            if headers.contains_key("divisioncode") {
                match division_code {
                    Some(code) if code > 0 => {}
                    _ => {
                        skipped += 1;
                        continue;
                    }
                }
            }


            // Check if category with same name already exists
            let existing: Option<String> = sqlx::query_scalar(
                "SELECT id FROM categories WHERE lower(name) = lower(?) AND is_deleted = 0",
            )
            .bind(&name)
            .fetch_optional(&mut *tx)
            .await?;

            if let Some(_id) = existing {
                // Category already exists; count as imported/reconciled
                imported += 1;
            } else {
                let cat_id = Uuid::new_v4().to_string();
                sqlx::query(
                    "INSERT INTO categories (id, name, parent_id, is_deleted) VALUES (?, ?, NULL, 0)",
                )
                .bind(&cat_id)
                .bind(&name)
                .execute(&mut *tx)
                .await?;

                let audit_id = Uuid::new_v4().to_string();
                let _ = sqlx::query(
                    "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description) VALUES (?, 'IMPORT', 'categories', ?, ?)",
                )
                .bind(audit_id)
                .bind(&cat_id)
                .bind(format!("Imported category: {}", name))
                .execute(&mut *tx)
                .await;

                imported += 1;
            }
        }

        tx.commit().await?;

        Ok(CsvImportResult {
            total_rows,
            imported,
            skipped,
            errors,
        })
    }

    // ------------------------------------------------------------------------
    // Products Import
    // ------------------------------------------------------------------------
    pub async fn import_products(
        pool: &SqlitePool,
        csv_content: &str,
    ) -> Result<CsvImportResult, AppError> {
        let rows = parse_csv(csv_content);
        if rows.is_empty() {
            return Err(AppError::Validation("CSV file is empty".to_string()));
        }

        let header_idx = find_header_row_index(
            &rows,
            &["Product Code", "Product Name", "MRP", "Division Code"],
        )
        .or_else(|| find_header_row_index(&rows, &["Product Code", "Product Name", "MRP"]))
        .or_else(|| find_header_row_index(&rows, &["Product Name", "SKU", "MRP"]))
        .or_else(|| find_header_row_index(&rows, &["Product Name", "SKU"]))
        .ok_or_else(|| {
            AppError::Validation(
                "Could not find valid product header row. Required: 'Product Code'/'SKU', 'Product Name', 'MRP'."
                    .to_string(),
            )
        })?;

        let headers = build_header_index(&rows[header_idx]);
        let mut tx = pool.begin().await?;

        let mut imported = 0usize;
        let mut skipped = 0usize;
        let mut errors = Vec::new();
        let total_rows = rows.len().saturating_sub(header_idx + 1);

        for (row_offset, row) in rows.iter().skip(header_idx + 1).enumerate() {
            let row_num = header_idx + 2 + row_offset;
            if is_row_empty(row) {
                continue;
            }

            let name = get_value(row, &headers, &["Product Name", "Name"]);
            let name = match name {
                Some(n) if !n.trim().is_empty() && !n.trim().eq_ignore_ascii_case("No Of Products") => {
                    n.trim().to_string()
                }
                _ => {
                    skipped += 1;
                    continue;
                }
            };

            let sku = get_value(row, &headers, &["Product Code", "SKU"]);
            let sku = match sku {
                Some(s) if !s.trim().is_empty() => s.trim().to_string(),
                _ => {
                    skipped += 1;
                    errors.push(format!("Row {}: Missing SKU/Product Code for '{}'", row_num, name));
                    continue;
                }
            };

            let division_code = get_value(row, &headers, &["Division Code", "Category Code"])
                .and_then(|v| parse_integer(&v));
            let category_name = get_value(row, &headers, &["Division Name", "Category Name", "Category"]);

            let min_stock_alert = get_value(
                row,
                &headers,
                &["Minimum Stock Level", "Stock Alert", "Stock Alert Cap"],
            )
            .and_then(|v| parse_integer(&v))
            .unwrap_or(10);

            let mrp_paise = get_value(row, &headers, &["MRP", "MRP/Price", "Price"])
                .and_then(|v| parse_decimal_to_paise(&v))
                .unwrap_or(0);

            let cost_price_paise = get_value(row, &headers, &["Avg Item Cost", "Cost Price", "Cost"])
                .and_then(|v| parse_decimal_to_paise(&v))
                .unwrap_or(0);

            let initial_stock = get_value(row, &headers, &["Opening Stock", "Current Stock", "Stock"])
                .and_then(|v| parse_integer(&v))
                .unwrap_or(0);

            let tax_rate = get_value(row, &headers, &["Tax Rate", "Tax%"])
                .and_then(|v| parse_float(&v))
                .unwrap_or(0.0);

            // Resolve or create category
            let mut resolved_cat_id: Option<String> = None;
            if let Some(cat_name) = category_name.filter(|n| !n.trim().is_empty()) {
                let trimmed = cat_name.trim();
                let existing_id: Option<String> = sqlx::query_scalar(
                    "SELECT id FROM categories WHERE lower(name) = lower(?) AND is_deleted = 0",
                )
                .bind(trimmed)
                .fetch_optional(&mut *tx)
                .await?;

                if let Some(id) = existing_id {
                    resolved_cat_id = Some(id);
                } else {
                    let new_id = Uuid::new_v4().to_string();
                    sqlx::query(
                        "INSERT INTO categories (id, name, parent_id, is_deleted) VALUES (?, ?, NULL, 0)",
                    )
                    .bind(&new_id)
                    .bind(trimmed)
                    .execute(&mut *tx)
                    .await?;
                    resolved_cat_id = Some(new_id);
                }
            } else if let Some(div_code) = division_code {
                if div_code > 0 {
                    let fallback_name = format!("Division {}", div_code);
                    let existing_id: Option<String> = sqlx::query_scalar(
                        "SELECT id FROM categories WHERE lower(name) = lower(?) AND is_deleted = 0",
                    )
                    .bind(&fallback_name)
                    .fetch_optional(&mut *tx)
                    .await?;

                    if let Some(id) = existing_id {
                        resolved_cat_id = Some(id);
                    } else {
                        let new_id = Uuid::new_v4().to_string();
                        sqlx::query(
                            "INSERT INTO categories (id, name, parent_id, is_deleted) VALUES (?, ?, NULL, 0)",
                        )
                        .bind(&new_id)
                        .bind(&fallback_name)
                        .execute(&mut *tx)
                        .await?;
                        resolved_cat_id = Some(new_id);
                    }
                }
            }

            // Check if product with this SKU exists
            let existing_prod: Option<(String, i64)> = sqlx::query_as(
                "SELECT id, current_stock FROM products WHERE sku = ?",
            )
            .bind(&sku)
            .fetch_optional(&mut *tx)
            .await?;

            if let Some((prod_id, current_stock)) = existing_prod {
                // Update product details
                sqlx::query(
                    r#"
                    UPDATE products SET
                        name = ?,
                        category_id = COALESCE(?, category_id),
                        mrp_paise = ?,
                        cost_price_paise = ?,
                        tax_rate = ?,
                        min_stock_alert = ?,
                        is_deleted = 0,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    "#,
                )
                .bind(&name)
                .bind(&resolved_cat_id)
                .bind(mrp_paise)
                .bind(cost_price_paise)
                .bind(tax_rate)
                .bind(min_stock_alert)
                .bind(&prod_id)
                .execute(&mut *tx)
                .await?;

                // If existing product had 0 stock and initial_stock > 0, set stock
                if current_stock == 0 && initial_stock > 0 {
                    sqlx::query("UPDATE products SET current_stock = ? WHERE id = ?")
                        .bind(initial_stock)
                        .bind(&prod_id)
                        .execute(&mut *tx)
                        .await?;

                    let sm_id = Uuid::new_v4().to_string();
                    let _ = sqlx::query(
                        r#"
                        INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                        VALUES (?, ?, ?, 'RESTOCK', NULL, 'Initial stock from CSV import')
                        "#,
                    )
                    .bind(sm_id)
                    .bind(&prod_id)
                    .bind(initial_stock)
                    .execute(&mut *tx)
                    .await;
                }

                imported += 1;
            } else {
                let prod_id = Uuid::new_v4().to_string();
                sqlx::query(
                    r#"
                    INSERT INTO products (
                        id, name, sku, category_id, mrp_paise, cost_price_paise,
                        tax_rate, current_stock, min_stock_alert, status, is_deleted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', 0)
                    "#,
                )
                .bind(&prod_id)
                .bind(&name)
                .bind(&sku)
                .bind(&resolved_cat_id)
                .bind(mrp_paise)
                .bind(cost_price_paise)
                .bind(tax_rate)
                .bind(initial_stock)
                .bind(min_stock_alert)
                .execute(&mut *tx)
                .await?;

                if initial_stock > 0 {
                    let sm_id = Uuid::new_v4().to_string();
                    let _ = sqlx::query(
                        r#"
                        INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                        VALUES (?, ?, ?, 'RESTOCK', NULL, 'Initial stock from CSV import')
                        "#,
                    )
                    .bind(sm_id)
                    .bind(&prod_id)
                    .bind(initial_stock)
                    .execute(&mut *tx)
                    .await;
                }

                let audit_id = Uuid::new_v4().to_string();
                let _ = sqlx::query(
                    "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description) VALUES (?, 'IMPORT', 'products', ?, ?)",
                )
                .bind(audit_id)
                .bind(&prod_id)
                .bind(format!("Imported product: {} (SKU: {})", name, sku))
                .execute(&mut *tx)
                .await;

                imported += 1;
            }
        }

        tx.commit().await?;

        Ok(CsvImportResult {
            total_rows,
            imported,
            skipped,
            errors,
        })
    }

    // ------------------------------------------------------------------------
    // Customers Import
    // ------------------------------------------------------------------------
    pub async fn import_customers(
        pool: &SqlitePool,
        csv_content: &str,
    ) -> Result<CsvImportResult, AppError> {
        let rows = parse_csv(csv_content);
        if rows.is_empty() {
            return Err(AppError::Validation("CSV file is empty".to_string()));
        }

        let header_idx = find_header_row_index(&rows, &["Customer Code", "Customer Name"])
            .or_else(|| find_header_row_index(&rows, &["Customer Name"]))
            .or_else(|| find_header_row_index(&rows, &["Name", "Phone"]))
            .ok_or_else(|| {
                AppError::Validation(
                    "Could not find valid customer header row. Required: 'Customer Code' and 'Customer Name' (or 'Name')."
                        .to_string(),
                )
            })?;

        let headers = build_header_index(&rows[header_idx]);
        let mut tx = pool.begin().await?;

        let mut imported = 0usize;
        let mut skipped = 0usize;
        let errors = Vec::new();
        let total_rows = rows.len().saturating_sub(header_idx + 1);

        for (_row_offset, row) in rows.iter().skip(header_idx + 1).enumerate() {
            if is_row_empty(row) {
                continue;
            }

            let customer_code = get_value(row, &headers, &["Customer Code", "Code", "ID"])
                .and_then(|v| parse_integer(&v));
            if headers.contains_key("customercode") {
                match customer_code {
                    Some(code) if code > 0 => {}
                    _ => {
                        skipped += 1;
                        continue;
                    }
                }
            }

            let name = get_value(row, &headers, &["Customer Name", "Name"]);

            let name = match name {
                Some(n) if !n.trim().is_empty() => n.trim().to_string(),
                _ => {
                    skipped += 1;
                    continue;
                }
            };

            let phone = get_value(row, &headers, &["Contact Number", "Phone", "Mobile"])
                .filter(|p| !p.trim().is_empty())
                .map(|p| p.trim().to_string());

            let address_part = get_value(row, &headers, &["Customer Address", "Address"]);
            let city_part = get_value(row, &headers, &["City"]);
            let pin_part = get_value(row, &headers, &["Pin Code", "Pincode", "Postal Code"]);

            let mut address_parts = Vec::new();
            if let Some(a) = address_part.filter(|v| !v.trim().is_empty()) {
                address_parts.push(a.trim().to_string());
            }
            if let Some(c) = city_part.filter(|v| !v.trim().is_empty()) {
                address_parts.push(c.trim().to_string());
            }
            if let Some(p) = pin_part.filter(|v| !v.trim().is_empty()) {
                address_parts.push(p.trim().to_string());
            }

            let full_address = if address_parts.is_empty() {
                None
            } else {
                Some(address_parts.join(", "))
            };

            // Look up existing customer by phone (if available) or by name
            let existing_id: Option<String> = if let Some(ref ph) = phone {
                sqlx::query_scalar("SELECT id FROM customers WHERE phone = ? AND is_deleted = 0")
                    .bind(ph)
                    .fetch_optional(&mut *tx)
                    .await?
            } else {
                sqlx::query_scalar(
                    "SELECT id FROM customers WHERE lower(name) = lower(?) AND is_deleted = 0",
                )
                .bind(&name)
                .fetch_optional(&mut *tx)
                .await?
            };

            if let Some(cust_id) = existing_id {
                sqlx::query(
                    r#"
                    UPDATE customers SET
                        name = ?,
                        phone = COALESCE(?, phone),
                        address = COALESCE(?, address),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    "#,
                )
                .bind(&name)
                .bind(&phone)
                .bind(&full_address)
                .bind(&cust_id)
                .execute(&mut *tx)
                .await?;

                imported += 1;
            } else {
                let cust_id = Uuid::new_v4().to_string();
                sqlx::query(
                    "INSERT INTO customers (id, name, phone, address, is_deleted) VALUES (?, ?, ?, ?, 0)",
                )
                .bind(&cust_id)
                .bind(&name)
                .bind(&phone)
                .bind(&full_address)
                .execute(&mut *tx)
                .await?;

                let audit_id = Uuid::new_v4().to_string();
                let _ = sqlx::query(
                    "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description) VALUES (?, 'IMPORT', 'customers', ?, ?)",
                )
                .bind(audit_id)
                .bind(&cust_id)
                .bind(format!("Imported customer: {}", name))
                .execute(&mut *tx)
                .await;

                imported += 1;
            }
        }

        tx.commit().await?;

        Ok(CsvImportResult {
            total_rows,
            imported,
            skipped,
            errors,
        })
    }

    // ------------------------------------------------------------------------
    // Bill History (Legacy Serieswise Sales) Import
    // ------------------------------------------------------------------------
    pub async fn import_bills(
        pool: &SqlitePool,
        csv_content: &str,
        source_file: Option<&str>,
    ) -> Result<CsvImportResult, AppError> {
        let rows = parse_csv(csv_content);
        if rows.is_empty() {
            return Err(AppError::Validation("CSV file is empty".to_string()));
        }

        let header_idx = find_header_row_index(
            &rows,
            &["Bill Date", "Bill Number", "Net amount", "Bill Time"],
        )
        .or_else(|| {
            find_header_row_index(
                &rows,
                &["Bill Date", "Invoice Number", "Net Amount", "Bill Time"],
            )
        })
        .or_else(|| find_header_row_index(&rows, &["Bill Date", "Bill Number", "Net amount"]))
        .or_else(|| find_header_row_index(&rows, &["Invoice Number", "Total Amount"]))
        .ok_or_else(|| {
            AppError::Validation(
                "Could not find valid bill header row. Required: 'Bill Date', 'Bill Number', 'Net amount', 'Bill Time'."
                    .to_string(),
            )
        })?;

        let headers = build_header_index(&rows[header_idx]);
        let mut tx = pool.begin().await?;

        let mut imported = 0usize;
        let mut skipped = 0usize;
        let mut errors = Vec::new();
        let total_rows = rows.len().saturating_sub(header_idx + 1);

        for (row_offset, row) in rows.iter().skip(header_idx + 1).enumerate() {
            let row_num = header_idx + 2 + row_offset;
            if is_row_empty(row) {
                continue;
            }

            let bill_number = get_value(row, &headers, &["Bill Number", "Invoice Number"]);
            let bill_number = match bill_number {
                Some(b) if !b.trim().is_empty() && !b.trim().eq_ignore_ascii_case("Grand Total") => {
                    b.trim().to_string()
                }
                _ => {
                    skipped += 1;
                    continue;
                }
            };

            let bill_date_str = get_value(row, &headers, &["Bill Date", "Date"]);
            let bill_time_str = get_value(row, &headers, &["Bill Time", "Time"]);

            let parsed_date = match bill_date_str.as_deref().and_then(parse_csv_date) {
                Some(d) => d,
                None => {
                    skipped += 1;
                    errors.push(format!("Row {}: Invalid date in bill '{}'", row_num, bill_number));
                    continue;
                }
            };

            let parsed_time = bill_time_str
                .as_deref()
                .map(parse_csv_time)
                .unwrap_or_else(|| NaiveTime::from_hms_opt(0, 0, 0).unwrap());

            let created_at = NaiveDateTime::new(parsed_date, parsed_time)
                .format("%Y-%m-%d %H:%M:%S")
                .to_string();

            let net_amount_paise = get_value(
                row,
                &headers,
                &["Net amount", "Net Amount", "Total Amount", "Amount", "Total"],
            )
            .and_then(|v| parse_decimal_to_paise(&v))
            .unwrap_or(0);

            let customer_name = get_value(row, &headers, &["Customer Name", "Customer"])
                .filter(|n| !n.trim().is_empty())
                .unwrap_or_else(|| "Walk-in Customer".to_string());

            let payment_method = infer_legacy_payment_method(&bill_number);
            let notes = format!(
                "Imported legacy bill from {}",
                source_file.unwrap_or("legacy CSV")
            );

            let new_sale_id = Uuid::new_v4().to_string();

            let res = sqlx::query(
                r#"
                INSERT INTO sales (
                    id, invoice_number, customer_id, customer_name, customer_phone, biller_name,
                    subtotal_paise, discount_paise, tax_paise, total_paise, paid_paise,
                    payment_method, status, fulfillment_status, notes, created_at
                ) VALUES (?, ?, NULL, ?, NULL, 'Legacy Import', ?, 0, 0, ?, ?, ?, 'PAID', 'FULFILLED', ?, ?)
                ON CONFLICT(invoice_number) DO UPDATE SET
                    total_paise = excluded.total_paise,
                    paid_paise = excluded.paid_paise,
                    subtotal_paise = excluded.subtotal_paise,
                    created_at = excluded.created_at,
                    customer_name = excluded.customer_name,
                    payment_method = excluded.payment_method,
                    notes = excluded.notes
                "#,
            )
            .bind(&new_sale_id)
            .bind(&bill_number)
            .bind(&customer_name)
            .bind(net_amount_paise)
            .bind(net_amount_paise)
            .bind(net_amount_paise)
            .bind(&payment_method)
            .bind(&notes)
            .bind(&created_at)
            .execute(&mut *tx)
            .await;

            match res {
                Ok(_) => {
                    imported += 1;
                }
                Err(e) => {
                    skipped += 1;
                    errors.push(format!("Row {}: Failed to import bill '{}': {}", row_num, bill_number, e));
                }
            }
        }

        tx.commit().await?;

        Ok(CsvImportResult {
            total_rows,
            imported,
            skipped,
            errors,
        })
    }
}

// ----------------------------------------------------------------------------
// Helper Parsing Functions
// ----------------------------------------------------------------------------

pub fn parse_csv(content: &str) -> Vec<Vec<String>> {
    let mut rows = Vec::new();
    if content.is_empty() {
        return rows;
    }

    // Strip BOM if present
    let content = content.strip_prefix('\u{feff}').unwrap_or(content);

    let mut current_row: Vec<String> = Vec::new();
    let mut current_field = String::new();
    let mut in_quotes = false;
    let mut chars = content.chars().peekable();

    while let Some(ch) = chars.next() {
        if ch == '"' {
            if in_quotes && chars.peek() == Some(&'"') {
                // Escaped quote
                current_field.push('"');
                chars.next();
            } else {
                in_quotes = !in_quotes;
            }
            continue;
        }

        if !in_quotes && ch == ',' {
            current_row.push(current_field.trim().to_string());
            current_field.clear();
            continue;
        }

        if !in_quotes && (ch == '\n' || ch == '\r') {
            current_row.push(current_field.trim().to_string());
            current_field.clear();
            if !current_row.is_empty() {
                rows.push(current_row);
                current_row = Vec::new();
            }
            if ch == '\r' && chars.peek() == Some(&'\n') {
                chars.next();
            }
            continue;
        }

        current_field.push(ch);
    }

    if !current_field.is_empty() || !current_row.is_empty() {
        current_row.push(current_field.trim().to_string());
        rows.push(current_row);
    }

    rows
}

pub fn normalize_header(value: &str) -> String {
    value
        .trim()
        .chars()
        .filter(|c| c.is_ascii_alphanumeric())
        .collect::<String>()
        .to_ascii_lowercase()
}

pub fn build_header_index(row: &[String]) -> HashMap<String, usize> {
    let mut index = HashMap::new();
    for (i, cell) in row.iter().enumerate() {
        let normalized = normalize_header(cell);
        if !normalized.is_empty() && !index.contains_key(&normalized) {
            index.insert(normalized, i);
        }
    }
    index
}

pub fn find_header_row_index(rows: &[Vec<String>], required_columns: &[&str]) -> Option<usize> {
    let required_normalized: Vec<String> = required_columns
        .iter()
        .map(|c| normalize_header(c))
        .filter(|s| !s.is_empty())
        .collect();

    if required_normalized.is_empty() {
        return None;
    }

    for (i, row) in rows.iter().enumerate() {
        let headers = build_header_index(row);
        let matches_all = required_normalized
            .iter()
            .all(|req| headers.contains_key(req));
        if matches_all {
            return Some(i);
        }
    }

    None
}

pub fn get_value(
    row: &[String],
    headers: &HashMap<String, usize>,
    candidates: &[&str],
) -> Option<String> {
    for candidate in candidates {
        let norm = normalize_header(candidate);
        if let Some(&idx) = headers.get(&norm) {
            if idx < row.len() {
                let val = row[idx].trim();
                if !val.is_empty() {
                    return Some(val.to_string());
                }
            }
        }
    }
    None
}

pub fn is_row_empty(row: &[String]) -> bool {
    row.iter().all(|f| f.trim().is_empty())
}

pub fn parse_integer(s: &str) -> Option<i64> {
    let cleaned: String = s.chars().filter(|c| c.is_ascii_digit() || *c == '-').collect();
    cleaned.parse::<i64>().ok()
}

pub fn parse_float(s: &str) -> Option<f64> {
    let cleaned: String = s.replace(',', "");
    cleaned.trim().parse::<f64>().ok()
}

pub fn parse_decimal_to_paise(s: &str) -> Option<i64> {
    let cleaned: String = s.replace(',', "");
    let float_val = cleaned.trim().parse::<f64>().ok()?;
    Some((float_val * 100.0).round() as i64)
}

pub fn infer_legacy_payment_method(bill_number: &str) -> String {
    let trimmed = bill_number.trim();
    if trimmed.is_empty() {
        return "Legacy Import".to_string();
    }
    match trimmed.chars().next().map(|c| c.to_ascii_uppercase()) {
        Some('C') => "Cash".to_string(),
        Some('X') => "Gift Card".to_string(),
        Some('K') => "Card".to_string(),
        Some('U') => "UPI".to_string(),
        _ => "Legacy Import".to_string(),
    }
}

pub fn parse_csv_date(raw: &str) -> Option<NaiveDate> {
    let trimmed = raw.trim();
    // Common date patterns
    let formats = [
        "%m/%d/%Y",
        "%d/%m/%Y",
        "%Y-%m-%d",
        "%d-%m-%Y",
        "%Y/%m/%d",
        "%d.%m.%Y",
    ];

    for fmt in formats {
        if let Ok(d) = NaiveDate::parse_from_str(trimmed, fmt) {
            return Some(d);
        }
    }

    // Try manual splitting for "M/d/yyyy" or "d/M/yyyy" if single-digit days/months
    let parts: Vec<&str> = trimmed.split(&['/', '-', '.'][..]).collect();
    if parts.len() == 3 {
        if let (Ok(p1), Ok(p2), Ok(p3)) = (
            parts[0].parse::<u32>(),
            parts[1].parse::<u32>(),
            parts[2].parse::<i32>(),
        ) {
            let year = if p3 < 100 { 2000 + p3 } else { p3 };
            // Check month/day/year
            if let Some(d) = NaiveDate::from_ymd_opt(year, p1, p2) {
                return Some(d);
            }
            // Check day/month/year
            if let Some(d) = NaiveDate::from_ymd_opt(year, p2, p1) {
                return Some(d);
            }
        }
    }

    None
}

pub fn parse_csv_time(raw: &str) -> NaiveTime {
    let trimmed = raw.trim().to_uppercase();
    let cleaned = trimmed.split_whitespace().collect::<Vec<_>>().join(" ");

    let formats = [
        "%I:%M %p",
        "%I.%M %p",
        "%I:%M%p",
        "%I.%M%p",
        "%H:%M:%S",
        "%H:%M",
    ];

    for fmt in formats {
        if let Ok(t) = NaiveTime::parse_from_str(&cleaned, fmt) {
            return t;
        }
    }

    // Fallback: extract numbers
    NaiveTime::from_hms_opt(0, 0, 0).unwrap()
}
