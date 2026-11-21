use sqlx::{Row, SqlitePool};
use uuid::Uuid;

use crate::application::{InventoryService, SaleAmendmentService, ValidationService};
use crate::domain::models::{
    CreateSaleItemPayload, CreateSalePayload, Sale, SaleItem, SaleResponse, UpdateSalePayload,
};
use crate::domain::rules::BusinessRules;
use crate::errors::AppError;
use crate::infrastructure::AppLogger;

pub struct SalesService;

impl SalesService {
    /// Validate requested quantities against current stock when negative stock
    /// is disabled in General Settings. Returns an `InsufficientStock` error
    /// naming the first product that would go below zero.
    async fn validate_requested_stock(
        pool: &SqlitePool,
        items: &[CreateSaleItemPayload],
    ) -> Result<(), AppError> {
        let mut totals: std::collections::HashMap<String, i64> = std::collections::HashMap::new();
        for item in items {
            if item.quantity <= 0 {
                return Err(AppError::Validation(
                    "Item quantity must be greater than 0".to_string(),
                ));
            }
            *totals.entry(item.product_id.clone()).or_insert(0) += item.quantity;
        }

        for (product_id, requested) in totals {
            let row = sqlx::query(
                "SELECT name, current_stock FROM products WHERE id = ? AND is_deleted = 0",
            )
            .bind(&product_id)
            .fetch_optional(pool)
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
        }

        Ok(())
    }

    pub async fn process_sale(
        pool: &SqlitePool,
        payload: CreateSalePayload,
    ) -> Result<SaleResponse, AppError> {
        ValidationService::validate_create_sale(&payload)?;
        AppLogger::debug("Sales", format!("Processing new sale with {} cart items", payload.items.len()));

        let allow_negative_stock = InventoryService::get_general_settings(pool)
            .await?
            .allow_negative_stock;

        if !allow_negative_stock {
            Self::validate_requested_stock(pool, &payload.items).await?;
        }

        let mut tx = pool.begin().await?;

        // 1. Resolve or create customer if needed (with phone deduplication)
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
        let mut subtotal_cents: i64 = 0;
        let mut total_tax_cents: i64 = 0;
        let mut sale_items: Vec<SaleItem> = Vec::new();

        for (idx, item_req) in payload.items.iter().enumerate() {
            let prod_row = sqlx::query(
                "SELECT id, name, sku, mrp_cents, cost_price_cents, tax_rate, current_stock
                 FROM products WHERE id = ? AND is_deleted = 0"
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
                sale_id: sale_id.clone(),
                product_id: prod_id.clone(),
                product_name: prod_name.clone(),
                product_sku: prod_sku.clone(),
                quantity: item_req.quantity,
                unit_price_cents: item_req.unit_price_cents,
                cost_price_cents,
                discount_cents: disc,
                tax_rate,
                tax_cents: tax_p,
                total_cents: item_total,
                item_order: idx as i64,
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
            .bind(&invoice_number)
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

        let overall_discount = std::cmp::max(0, payload.discount_cents.unwrap_or(0));
        let total_cents = std::cmp::max(0, subtotal_cents - overall_discount);
        let paid_cents = std::cmp::max(0, payload.paid_cents);
        let change_cents = std::cmp::max(0, paid_cents - total_cents);

        let status = if paid_cents >= total_cents {
            "PAID"
        } else {
            "PARTIALLY_PAID"
        };

        let now_str = chrono::Utc::now().to_rfc3339();
        let clean_notes = payload.notes.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(String::from);
        let sale = Sale {
            id: sale_id.clone(),
            invoice_number: invoice_number.clone(),
            customer_id: resolved_customer_id,
            customer_name: cust_name.map(String::from),
            customer_phone: cust_phone.map(String::from),
            biller_name: Some("Cashier".to_string()),
            subtotal_cents,
            discount_cents: overall_discount,
            tax_cents: total_tax_cents,
            total_cents,
            paid_cents,
            payment_method: payload.payment_method.to_uppercase(),
            status: status.to_string(),
            fulfillment_status: "FULFILLED".to_string(),
            notes: clean_notes,
            created_at: now_str,
        };

        // 4. Insert sales row FIRST so foreign key references to sales(id) succeed
        sqlx::query(
            "INSERT INTO sales (id, invoice_number, customer_id, customer_name, customer_phone, biller_name, subtotal_cents, discount_cents, tax_cents, total_cents, paid_cents, payment_method, status, fulfillment_status, notes, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .bind(&sale.id)
        .bind(&sale.invoice_number)
        .bind(&sale.customer_id)
        .bind(&sale.customer_name)
        .bind(&sale.customer_phone)
        .bind(&sale.biller_name)
        .bind(sale.subtotal_cents)
        .bind(sale.discount_cents)
        .bind(sale.tax_cents)
        .bind(sale.total_cents)
        .bind(sale.paid_cents)
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
        }

        // 6. Audit log
        let audit_id = Uuid::new_v4().to_string();
        let desc = format!("Completed sale {} for total {}", invoice_number, total_cents);
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'SALE_CREATED', 'SALE', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(&sale.id)
        .bind(&desc)
        .execute(&mut *tx)
        .await?;

        AppLogger::info("Sales", format!("Sale {} committed: total {}", sale.invoice_number, sale.total_cents));
        tx.commit().await?;

        Ok(SaleResponse {
            sale,
            items: sale_items,
            change_cents,
        })
    }

    #[inline]
    pub async fn update_sale(
        pool: &SqlitePool,
        payload: UpdateSalePayload,
    ) -> Result<SaleResponse, AppError> {
        SaleAmendmentService::update_sale(pool, payload).await
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
                        subtotal_cents, discount_cents, tax_cents, total_cents, paid_cents,
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
                        subtotal_cents, discount_cents, tax_cents, total_cents, paid_cents,
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
                    subtotal_cents, discount_cents, tax_cents, total_cents, paid_cents,
                    payment_method, status, fulfillment_status, notes,
                    created_at
             FROM sales WHERE id = ?1 OR invoice_number = ?1"
        )
        .bind(sale_id)
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Sale {} not found", sale_id)))?;

        let items = sqlx::query_as::<_, SaleItem>(
            "SELECT id, sale_id, product_id, product_name, product_sku, quantity,
                    unit_price_cents, cost_price_cents, discount_cents, CAST(tax_rate AS REAL) as tax_rate, tax_cents, total_cents,
                    item_order
             FROM sale_items
             WHERE sale_id = ?1
             ORDER BY item_order ASC, rowid ASC"
        )
        .bind(&sale.id)
        .fetch_all(pool)
        .await?;

        let change_cents = std::cmp::max(0, sale.paid_cents - sale.total_cents);

        Ok(SaleResponse {
            sale,
            items,
            change_cents,
        })
    }

    #[inline]
    pub async fn cancel_sale(
        pool: &SqlitePool,
        sale_id: &str,
        reason: &str,
    ) -> Result<Sale, AppError> {
        SaleAmendmentService::cancel_sale(pool, sale_id, reason).await
    }
}
