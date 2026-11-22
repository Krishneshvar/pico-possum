use sqlx::{Row, SqlitePool};
use uuid::Uuid;

use crate::application::{SettingsService, ValidationService};
use crate::domain::models::{Sale, SaleItem, SaleResponse, UpdateSalePayload};
use crate::errors::AppError;
use crate::infrastructure::AppLogger;

pub struct SaleAmendmentService;

impl SaleAmendmentService {
    async fn validate_update_stock(
        tx: &mut sqlx::Transaction<'_, sqlx::Sqlite>,
        product_id: &str,
        requested: i64,
    ) -> Result<(), AppError> {
        let row = sqlx::query("SELECT name, current_stock FROM products WHERE id = ?")
            .bind(product_id)
            .fetch_optional(&mut **tx)
            .await?
            .ok_or_else(|| AppError::NotFound(format!("Product {} not found", product_id)))?;

        let available: i64 = row.get("current_stock");
        if requested > available {
            return Err(AppError::InsufficientStock {
                product: row.get("name"),
                available,
                requested,
            });
        }

        Ok(())
    }

    pub async fn update_sale(
        pool: &SqlitePool,
        payload: UpdateSalePayload,
    ) -> Result<SaleResponse, AppError> {
        ValidationService::validate_update_sale(&payload)?;

        let allow_negative_stock = SettingsService::get_general_settings(pool)
            .await?
            .allow_negative_stock;

        let mut tx = pool.begin().await?;

        // 1. Fetch current sale
        let existing_sale = sqlx::query_as::<_, Sale>(
            "SELECT id, invoice_number, customer_id, customer_name, customer_phone, biller_name,
                    subtotal_cents, discount_cents, tax_cents, total_cents, paid_cents,
                    payment_method, status, fulfillment_status, notes,
                    created_at
             FROM sales WHERE id = ?"
        )
        .bind(&payload.sale_id)
        .fetch_optional(&mut *tx)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Sale {} not found", payload.sale_id)))?;

        if existing_sale.status == "CANCELLED" {
            return Err(AppError::Validation("Cannot edit a cancelled bill".to_string()));
        }

        // 2. Fetch existing items for this sale
        let old_items = sqlx::query_as::<_, SaleItem>(
            "SELECT id, sale_id, product_id, product_name, product_sku, quantity,
                    unit_price_cents, cost_price_cents, discount_cents, CAST(tax_rate AS REAL) as tax_rate, tax_cents, total_cents,
                    item_order
             FROM sale_items
             WHERE sale_id = ?
             ORDER BY item_order ASC, rowid ASC"
        )
        .bind(&payload.sale_id)
        .fetch_all(&mut *tx)
        .await?;

        // 3. Map old quantities by product_id
        let mut old_qty_map: std::collections::HashMap<String, i64> = std::collections::HashMap::new();
        for it in &old_items {
            *old_qty_map.entry(it.product_id.clone()).or_insert(0) += it.quantity;
        }

        // 4. Map new quantities by product_id
        let mut new_qty_map: std::collections::HashMap<String, i64> = std::collections::HashMap::new();
        for it in &payload.items {
            *new_qty_map.entry(it.product_id.clone()).or_insert(0) += it.quantity;
        }

        // 5. Compute net inventory adjustments and record stock movements
        let mut all_product_ids: std::collections::HashSet<String> = std::collections::HashSet::new();
        for pid in old_qty_map.keys() {
            all_product_ids.insert(pid.clone());
        }
        for pid in new_qty_map.keys() {
            all_product_ids.insert(pid.clone());
        }

        for pid in &all_product_ids {
            let old_q = old_qty_map.get(pid).copied().unwrap_or(0);
            let new_q = new_qty_map.get(pid).copied().unwrap_or(0);
            let delta_q = new_q - old_q;

            if delta_q > 0 {
                if !allow_negative_stock {
                    Self::validate_update_stock(&mut tx, pid, delta_q).await?;
                }

                sqlx::query(
                    "UPDATE products SET current_stock = current_stock - ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                )
                .bind(delta_q)
                .bind(pid)
                .execute(&mut *tx)
                .await?;

                let movement_id = Uuid::new_v4().to_string();
                let note = format!("Bill {} adjusted: +{} qty", existing_sale.invoice_number, delta_q);
                sqlx::query(
                    "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                     VALUES (?, ?, ?, 'BILL_ADJUSTMENT', ?, ?)"
                )
                .bind(&movement_id)
                .bind(pid)
                .bind(-delta_q)
                .bind(&existing_sale.invoice_number)
                .bind(&note)
                .execute(&mut *tx)
                .await?;
            } else if delta_q < 0 {
                let returned_q = -delta_q;
                sqlx::query(
                    "UPDATE products SET current_stock = current_stock + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                )
                .bind(returned_q)
                .bind(pid)
                .execute(&mut *tx)
                .await?;

                let movement_id = Uuid::new_v4().to_string();
                let note = format!("Bill {} edited: returned {} qty", existing_sale.invoice_number, returned_q);
                sqlx::query(
                    "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                     VALUES (?, ?, ?, 'RETURN', ?, ?)"
                )
                .bind(&movement_id)
                .bind(pid)
                .bind(returned_q)
                .bind(&existing_sale.invoice_number)
                .bind(&note)
                .execute(&mut *tx)
                .await?;
            }
        }

        // 6. Delete old sale_items and construct new ones
        sqlx::query("DELETE FROM sale_items WHERE sale_id = ?")
            .bind(&payload.sale_id)
            .execute(&mut *tx)
            .await?;

        let mut subtotal_cents: i64 = 0;
        let mut total_tax_cents: i64 = 0;
        let mut new_sale_items: Vec<SaleItem> = Vec::new();

        for (idx, item_req) in payload.items.iter().enumerate() {
            let prod_row = sqlx::query(
                "SELECT id, name, sku, mrp_cents, cost_price_cents, tax_rate, current_stock
                 FROM products WHERE id = ?"
            )
            .bind(&item_req.product_id)
            .fetch_optional(&mut *tx)
            .await?
            .ok_or_else(|| AppError::NotFound(format!("Product {} not found", item_req.product_id)))?;

            let prod_id: String = prod_row.get("id");
            let prod_name: String = prod_row.get("name");
            let prod_sku: Option<String> = prod_row.get("sku");
            let mrp_cents: i64 = prod_row.get("mrp_cents");
            let cost_price_cents: i64 = prod_row.get("cost_price_cents");
            let tax_rate: f64 = prod_row.get("tax_rate");

            ValidationService::validate_item_unit_price(&prod_name, item_req.unit_price_cents, mrp_cents)?;

            let disc = std::cmp::max(0, item_req.discount_cents.unwrap_or(0));
            let gross = item_req.unit_price_cents * item_req.quantity;
            let item_total = std::cmp::max(0, gross - disc);
            subtotal_cents += item_total;

            let tax_p = ((item_total as f64) * (tax_rate / 100.0)).round() as i64;
            total_tax_cents += tax_p;

            let sale_item = SaleItem {
                id: Uuid::new_v4().to_string(),
                sale_id: payload.sale_id.clone(),
                product_id: prod_id,
                product_name: prod_name,
                product_sku: prod_sku,
                quantity: item_req.quantity,
                unit_price_cents: item_req.unit_price_cents,
                cost_price_cents,
                discount_cents: disc,
                tax_rate,
                tax_cents: tax_p,
                total_cents: item_total,
                item_order: idx as i64,
            };

            sqlx::query(
                "INSERT INTO sale_items (id, sale_id, product_id, product_name, product_sku, quantity, unit_price_cents, cost_price_cents, discount_cents, tax_rate, tax_cents, total_cents, item_order)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            .bind(&sale_item.id)
            .bind(&sale_item.sale_id)
            .bind(&sale_item.product_id)
            .bind(&sale_item.product_name)
            .bind(&sale_item.product_sku)
            .bind(sale_item.quantity)
            .bind(sale_item.unit_price_cents)
            .bind(sale_item.cost_price_cents)
            .bind(sale_item.discount_cents)
            .bind(sale_item.tax_rate)
            .bind(sale_item.tax_cents)
            .bind(sale_item.total_cents)
            .bind(sale_item.item_order)
            .execute(&mut *tx)
            .await?;

            new_sale_items.push(sale_item);
        }

        // 7. Resolve customer (with phone deduplication)
        let cust_name = payload.customer_name.as_deref().map(str::trim).filter(|s| !s.is_empty());
        let cust_phone = payload.customer_phone.as_deref().map(str::trim).filter(|s| !s.is_empty());
        let cust_addr = payload.customer_address.as_deref().map(str::trim).filter(|s| !s.is_empty());

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
            if let Some(phone) = cust_phone {
                let existing_by_phone: Option<String> = sqlx::query_scalar(
                    "SELECT id FROM customers WHERE phone = ? AND is_deleted = 0 LIMIT 1"
                )
                .bind(phone)
                .fetch_optional(&mut *tx)
                .await?;

                if let Some(existing_id) = existing_by_phone {
                    resolved_customer_id = Some(existing_id);
                } else {
                    let existing_del: Option<String> = sqlx::query_scalar(
                        "SELECT id FROM customers WHERE phone = ? AND is_deleted = 1 LIMIT 1"
                    )
                    .bind(phone)
                    .fetch_optional(&mut *tx)
                    .await?;

                    if let Some(del_id) = existing_del {
                        sqlx::query(
                            "UPDATE customers SET name = ?, address = ?, is_deleted = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                        )
                        .bind(cust_name.unwrap_or("Customer"))
                        .bind(cust_addr.unwrap_or(""))
                        .bind(&del_id)
                        .execute(&mut *tx)
                        .await?;
                        resolved_customer_id = Some(del_id);
                    }
                }
            }
        }

        if resolved_customer_id.is_none() {
            if let Some(name) = cust_name {
                let new_cust_id = Uuid::new_v4().to_string();
                sqlx::query(
                    "INSERT INTO customers (id, name, phone, address, is_deleted)
                     VALUES (?, ?, ?, ?, 0)"
                )
                .bind(&new_cust_id)
                .bind(name)
                .bind(cust_phone.unwrap_or(""))
                .bind(cust_addr.unwrap_or(""))
                .execute(&mut *tx)
                .await?;

                resolved_customer_id = Some(new_cust_id);
            }
        }

        // 8. Financial totals
        let overall_discount = std::cmp::max(0, payload.discount_cents.unwrap_or(0));
        let total_cents = std::cmp::max(0, subtotal_cents - overall_discount);
        let paid_cents = std::cmp::max(0, payload.paid_cents);
        let change_cents = std::cmp::max(0, paid_cents - total_cents);

        let status = if paid_cents >= total_cents {
            "PAID"
        } else {
            "PARTIALLY_PAID"
        };

        let payment_method = payload.payment_method.to_uppercase();
        let clean_notes = payload.notes.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(String::from);

        sqlx::query(
            "UPDATE sales SET
                customer_id = ?,
                customer_name = ?,
                customer_phone = ?,
                subtotal_cents = ?,
                discount_cents = ?,
                tax_cents = ?,
                total_cents = ?,
                paid_cents = ?,
                payment_method = ?,
                status = ?,
                notes = ?
             WHERE id = ?"
        )
        .bind(&resolved_customer_id)
        .bind(cust_name)
        .bind(cust_phone)
        .bind(subtotal_cents)
        .bind(overall_discount)
        .bind(total_tax_cents)
        .bind(total_cents)
        .bind(paid_cents)
        .bind(&payment_method)
        .bind(status)
        .bind(&clean_notes)
        .bind(&payload.sale_id)
        .execute(&mut *tx)
        .await?;

        // 9. Audit log entry
        let audit_id = Uuid::new_v4().to_string();
        let pm_desc = if existing_sale.payment_method != payment_method {
            format!(" | Payment changed: {} -> {}", existing_sale.payment_method, payment_method)
        } else {
            String::new()
        };
        let desc = format!(
            "Edited bill #{}: Total: {} -> {}. Payment: {}{}",
            existing_sale.invoice_number, existing_sale.total_cents, total_cents, payment_method, pm_desc
        );
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'SALE_EDITED', 'SALE', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(&payload.sale_id)
        .bind(&desc)
        .execute(&mut *tx)
        .await?;

        AppLogger::info("Sales", format!("Bill {} amended: {} items, total {}", existing_sale.invoice_number, new_sale_items.len(), total_cents));
        tx.commit().await?;

        let updated_sale = Sale {
            id: payload.sale_id.clone(),
            invoice_number: existing_sale.invoice_number,
            customer_id: resolved_customer_id,
            customer_name: cust_name.map(String::from),
            customer_phone: cust_phone.map(String::from),
            biller_name: existing_sale.biller_name,
            subtotal_cents,
            discount_cents: overall_discount,
            tax_cents: total_tax_cents,
            total_cents,
            paid_cents,
            payment_method,
            status: status.to_string(),
            fulfillment_status: existing_sale.fulfillment_status,
            notes: clean_notes,
            created_at: existing_sale.created_at,
        };

        Ok(SaleResponse {
            sale: updated_sale,
            items: new_sale_items,
            change_cents,
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
                    subtotal_cents, discount_cents, tax_cents, total_cents, paid_cents,
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

        let clean_reason = match reason.trim() {
            "" => "Customer request / void",
            r => r,
        };

        for row in item_rows {
            let product_id: String = row.get("product_id");
            let quantity: i64 = row.get("quantity");
            let movement_id = Uuid::new_v4().to_string();
            let note = format!("Cancelled sale {}: {}", sale.invoice_number, clean_reason);

            sqlx::query(
                "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                 VALUES (?, ?, ?, 'CANCELLED', ?, ?)"
            )
            .bind(&movement_id).bind(&product_id).bind(quantity).bind(&sale.invoice_number).bind(&note)
            .execute(&mut *tx).await?;

            sqlx::query("UPDATE products SET current_stock = current_stock + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
                .bind(quantity).bind(&product_id).execute(&mut *tx).await?;
        }

        let updated_notes = match &sale.notes {
            Some(e) if !e.trim().is_empty() => format!("{} | [Cancelled: {}]", e.trim(), clean_reason),
            _ => format!("[Cancelled: {}]", clean_reason),
        };

        sqlx::query("UPDATE sales SET status = 'CANCELLED', fulfillment_status = 'CANCELLED', notes = ? WHERE id = ?")
            .bind(&updated_notes).bind(sale_id).execute(&mut *tx).await?;

        let audit_id = Uuid::new_v4().to_string();
        let desc = format!("Cancelled sale {}. Reason: {}", sale.invoice_number, clean_reason);
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'SALE_CANCELLED', 'SALE', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id).bind(sale_id).bind(&desc).execute(&mut *tx).await?;

        AppLogger::info("Sales", format!("Bill {} cancelled. Reason: {}", sale.invoice_number, clean_reason));
        tx.commit().await?;

        let mut updated = sale;
        updated.status = "CANCELLED".to_string();
        updated.fulfillment_status = "CANCELLED".to_string();
        updated.notes = Some(updated_notes);
        Ok(updated)
    }
}
