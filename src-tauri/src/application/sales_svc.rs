use sqlx::{Row, SqlitePool};
use uuid::Uuid;

use crate::domain::models::{CreateSalePayload, Sale, SaleItem, SaleResponse};
use crate::domain::rules::BusinessRules;
use crate::errors::AppError;

pub struct SalesService;

impl SalesService {
    pub async fn process_sale(
        pool: &SqlitePool,
        payload: CreateSalePayload,
    ) -> Result<SaleResponse, AppError> {
        if payload.items.is_empty() {
            return Err(AppError::Validation("Cart cannot be empty".to_string()));
        }

        let mut tx = pool.begin().await?;

        // 1. Resolve or create customer if needed
        let mut resolved_customer_id = None;
        if let Some(ref cust_id) = payload.customer_id {
            if !cust_id.trim().is_empty() {
                let exists: bool = sqlx::query_scalar(
                    "SELECT EXISTS(SELECT 1 FROM customers WHERE id = ? AND is_deleted = 0)"
                )
                .bind(cust_id.trim())
                .fetch_one(&mut *tx)
                .await
                .unwrap_or(false);

                if exists {
                    resolved_customer_id = Some(cust_id.trim().to_string());
                }
            }
        }

        if resolved_customer_id.is_none() {
            if let Some(ref name) = payload.customer_name {
                if !name.trim().is_empty() {
                    let new_cust_id = Uuid::new_v4().to_string();
                    sqlx::query(
                        "INSERT INTO customers (id, name, phone, address, is_deleted)
                         VALUES (?, ?, ?, ?, 0)"
                    )
                    .bind(&new_cust_id)
                    .bind(name.trim())
                    .bind(payload.customer_phone.as_deref().unwrap_or("").trim())
                    .bind(payload.customer_address.as_deref().unwrap_or("").trim())
                    .execute(&mut *tx)
                    .await?;

                    resolved_customer_id = Some(new_cust_id);
                }
            }
        }

        // 2. Generate sequential invoice number
        let payment_code = BusinessRules::payment_method_code(&payload.payment_method);
        let seq_row = sqlx::query(
            "INSERT INTO invoice_sequences (payment_code, last_sequence)
             VALUES (?, 1)
             ON CONFLICT(payment_code) DO UPDATE SET last_sequence = last_sequence + 1
             RETURNING last_sequence"
        )
        .bind(payment_code)
        .fetch_one(&mut *tx)
        .await?;

        let last_seq: i64 = seq_row.get(0);
        let invoice_number = BusinessRules::generate_invoice_number(&payload.payment_method, last_seq);

        // 3. Calculate line items, deduct stock and prepare SaleItem models
        let sale_id = Uuid::new_v4().to_string();
        let mut subtotal_paise: i64 = 0;
        let mut total_tax_paise: i64 = 0;
        let mut sale_items: Vec<SaleItem> = Vec::new();

        for item_req in &payload.items {
            if item_req.quantity <= 0 {
                return Err(AppError::Validation("Item quantity must be greater than 0".to_string()));
            }

            let prod_row = sqlx::query(
                "SELECT id, name, sku, mrp_paise, cost_price_paise, tax_rate, current_stock
                 FROM products WHERE id = ? AND is_deleted = 0"
            )
            .bind(&item_req.product_id)
            .fetch_optional(&mut *tx)
            .await?
            .ok_or_else(|| AppError::NotFound(format!("Product {} not found", item_req.product_id)))?;

            let prod_id: String = prod_row.get("id");
            let prod_name: String = prod_row.get("name");
            let prod_sku: Option<String> = prod_row.get("sku");
            let cost_price_paise: i64 = prod_row.get("cost_price_paise");
            let tax_rate: f64 = prod_row.get("tax_rate");

            let disc = item_req.discount_paise.unwrap_or(0);
            let item_total = (item_req.unit_price_paise * item_req.quantity) - disc;
            subtotal_paise += item_total;

            let tax_p = ((item_total as f64) * (tax_rate / 100.0)).round() as i64;
            total_tax_paise += tax_p;

            let sale_item = SaleItem {
                id: Uuid::new_v4().to_string(),
                sale_id: sale_id.clone(),
                product_id: prod_id.clone(),
                product_name: prod_name.clone(),
                product_sku: prod_sku.clone(),
                quantity: item_req.quantity,
                unit_price_paise: item_req.unit_price_paise,
                cost_price_paise,
                discount_paise: disc,
                tax_rate,
                tax_paise: tax_p,
                total_paise: item_total,
            };

            // Deduct stock from ledger and update current_stock
            let movement_id = Uuid::new_v4().to_string();
            sqlx::query(
                "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                 VALUES (?, ?, ?, 'SALE', ?, ?)"
            )
            .bind(&movement_id)
            .bind(&prod_id)
            .bind(-item_req.quantity)
            .bind(&sale_id)
            .bind(&invoice_number)
            .execute(&mut *tx)
            .await?;

            sqlx::query(
                "UPDATE products SET current_stock = current_stock - ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
            )
            .bind(item_req.quantity)
            .bind(&prod_id)
            .execute(&mut *tx)
            .await?;

            sale_items.push(sale_item);
        }

        let overall_discount = payload.discount_paise.unwrap_or(0);
        let total_paise = std::cmp::max(0, subtotal_paise - overall_discount);
        let paid_paise = payload.paid_paise;
        let change_paise = std::cmp::max(0, paid_paise - total_paise);

        let status = if paid_paise >= total_paise {
            "PAID"
        } else {
            "PARTIALLY_PAID"
        };

        let now_str = chrono::Utc::now().to_rfc3339();
        let sale = Sale {
            id: sale_id.clone(),
            invoice_number: invoice_number.clone(),
            customer_id: resolved_customer_id,
            customer_name: payload.customer_name,
            customer_phone: payload.customer_phone,
            biller_name: Some("Cashier".to_string()),
            subtotal_paise,
            discount_paise: overall_discount,
            tax_paise: total_tax_paise,
            total_paise,
            paid_paise,
            payment_method: payload.payment_method.to_uppercase(),
            status: status.to_string(),
            fulfillment_status: "FULFILLED".to_string(),
            notes: payload.notes,
            created_at: now_str,
        };

        // 4. Insert sales row FIRST so foreign key references to sales(id) succeed
        sqlx::query(
            "INSERT INTO sales (id, invoice_number, customer_id, customer_name, customer_phone, biller_name, subtotal_paise, discount_paise, tax_paise, total_paise, paid_paise, payment_method, status, fulfillment_status, notes, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .bind(&sale.id)
        .bind(&sale.invoice_number)
        .bind(&sale.customer_id)
        .bind(&sale.customer_name)
        .bind(&sale.customer_phone)
        .bind(&sale.biller_name)
        .bind(sale.subtotal_paise)
        .bind(sale.discount_paise)
        .bind(sale.tax_paise)
        .bind(sale.total_paise)
        .bind(sale.paid_paise)
        .bind(&sale.payment_method)
        .bind(&sale.status)
        .bind(&sale.fulfillment_status)
        .bind(&sale.notes)
        .bind(&sale.created_at)
        .execute(&mut *tx)
        .await?;

        // 5. Insert sale_items (referencing sales(id))
        for sale_item in &sale_items {
            sqlx::query(
                "INSERT INTO sale_items (id, sale_id, product_id, product_name, product_sku, quantity, unit_price_paise, cost_price_paise, discount_paise, tax_rate, tax_paise, total_paise)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            .bind(&sale_item.id)
            .bind(&sale_item.sale_id)
            .bind(&sale_item.product_id)
            .bind(&sale_item.product_name)
            .bind(&sale_item.product_sku)
            .bind(sale_item.quantity)
            .bind(sale_item.unit_price_paise)
            .bind(sale_item.cost_price_paise)
            .bind(sale_item.discount_paise)
            .bind(sale_item.tax_rate)
            .bind(sale_item.tax_paise)
            .bind(sale_item.total_paise)
            .execute(&mut *tx)
            .await?;
        }

        // 6. Audit log
        let audit_id = Uuid::new_v4().to_string();
        let desc = format!("Completed sale {} for total {} paise", invoice_number, total_paise);
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'SALE_CREATED', 'SALE', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(&sale.id)
        .bind(&desc)
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;

        Ok(SaleResponse {
            sale,
            items: sale_items,
            change_paise,
        })
    }

    pub async fn get_sales(
        pool: &SqlitePool,
        limit: i64,
        offset: i64,
        query: Option<String>,
    ) -> Result<Vec<Sale>, AppError> {
        let sales = if let Some(q) = query {
            let search = format!("%{}%", q.trim());
            sqlx::query_as::<_, Sale>(
                "SELECT id, invoice_number, customer_id, customer_name, customer_phone, biller_name,
                        subtotal_paise, discount_paise, tax_paise, total_paise, paid_paise,
                        payment_method, status, fulfillment_status, notes,
                        created_at
                 FROM sales
                 WHERE invoice_number LIKE ? OR customer_name LIKE ? OR customer_phone LIKE ?
                 ORDER BY created_at DESC LIMIT ? OFFSET ?"
            )
            .bind(&search)
            .bind(&search)
            .bind(&search)
            .bind(limit)
            .bind(offset)
            .fetch_all(pool)
            .await?
        } else {
            sqlx::query_as::<_, Sale>(
                "SELECT id, invoice_number, customer_id, customer_name, customer_phone, biller_name,
                        subtotal_paise, discount_paise, tax_paise, total_paise, paid_paise,
                        payment_method, status, fulfillment_status, notes,
                        created_at
                 FROM sales
                 ORDER BY created_at DESC LIMIT ? OFFSET ?"
            )
            .bind(limit)
            .bind(offset)
            .fetch_all(pool)
            .await?
        };

        Ok(sales)
    }

    pub async fn get_sale_details(
        pool: &SqlitePool,
        sale_id: &str,
    ) -> Result<SaleResponse, AppError> {
        let sale = sqlx::query_as::<_, Sale>(
            "SELECT id, invoice_number, customer_id, customer_name, customer_phone, biller_name,
                    subtotal_paise, discount_paise, tax_paise, total_paise, paid_paise,
                    payment_method, status, fulfillment_status, notes,
                    created_at
             FROM sales WHERE id = ?"
        )
        .bind(sale_id)
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Sale {} not found", sale_id)))?;

        let items = sqlx::query_as::<_, SaleItem>(
            "SELECT id, sale_id, product_id, product_name, product_sku, quantity,
                    unit_price_paise, cost_price_paise, discount_paise, CAST(tax_rate AS REAL) as tax_rate, tax_paise, total_paise
             FROM sale_items WHERE sale_id = ?"
        )
        .bind(sale_id)
        .fetch_all(pool)
        .await?;

        let change_paise = std::cmp::max(0, sale.paid_paise - sale.total_paise);

        Ok(SaleResponse {
            sale,
            items,
            change_paise,
        })
    }

    pub async fn cancel_sale(
        pool: &SqlitePool,
        sale_id: &str,
        reason: &str,
    ) -> Result<Sale, AppError> {
        let mut tx = pool.begin().await?;

        let sale = sqlx::query_as::<_, Sale>(
            "SELECT id, invoice_number, customer_id, customer_name, customer_phone, biller_name,
                    subtotal_paise, discount_paise, tax_paise, total_paise, paid_paise,
                    payment_method, status, fulfillment_status, notes,
                    created_at
             FROM sales WHERE id = ?"
        )
        .bind(sale_id)
        .fetch_optional(&mut *tx)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Sale {} not found", sale_id)))?;

        if sale.status == "CANCELLED" {
            return Err(AppError::Validation("Sale is already cancelled".to_string()));
        }

        // Return items to inventory
        let item_rows = sqlx::query(
            "SELECT product_id, quantity FROM sale_items WHERE sale_id = ?"
        )
        .bind(sale_id)
        .fetch_all(&mut *tx)
        .await?;

        for row in item_rows {
            let product_id: String = row.get("product_id");
            let quantity: i64 = row.get("quantity");
            let movement_id = Uuid::new_v4().to_string();
            let note = format!("Cancelled sale {}: {}", sale.invoice_number, reason);

            sqlx::query(
                "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                 VALUES (?, ?, ?, 'RETURN', ?, ?)"
            )
            .bind(&movement_id)
            .bind(&product_id)
            .bind(quantity)
            .bind(sale_id)
            .bind(&note)
            .execute(&mut *tx)
            .await?;

            sqlx::query(
                "UPDATE products SET current_stock = current_stock + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
            )
            .bind(quantity)
            .bind(&product_id)
            .execute(&mut *tx)
            .await?;
        }

        // Update sale status
        sqlx::query(
            "UPDATE sales SET status = 'CANCELLED', notes = ? WHERE id = ?"
        )
        .bind(reason)
        .bind(sale_id)
        .execute(&mut *tx)
        .await?;

        // Audit log
        let audit_id = Uuid::new_v4().to_string();
        let desc = format!("Cancelled sale {}. Reason: {}", sale.invoice_number, reason);
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'SALE_CANCELLED', 'SALE', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(sale_id)
        .bind(&desc)
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;

        let mut updated = sale;
        updated.status = "CANCELLED".to_string();
        updated.notes = Some(reason.to_string());
        Ok(updated)
    }
}
