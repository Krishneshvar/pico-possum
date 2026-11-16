use sqlx::{Row, SqlitePool};

use crate::domain::models::{
    MultiYearSalesPoint, ProductFlowItem, SalesBreakdownItem, SalesReportSummary, StockMovement,
    TopSellingCategory, TopSellingProduct,
};
use crate::errors::AppError;

pub struct ReportsService;

impl ReportsService {
    pub async fn get_stock_movements(
        pool: &SqlitePool,
        product_id: Option<String>,
        from_date: Option<String>,
        to_date: Option<String>,
        limit: i64,
    ) -> Result<Vec<StockMovement>, AppError> {
        let from = from_date.unwrap_or_else(|| "1970-01-01".to_string());
        let to = to_date.unwrap_or_else(|| "2099-12-31".to_string());

        let movements = sqlx::query_as::<_, StockMovement>(
            "SELECT sm.id, sm.product_id, p.name as product_name, p.sku as product_sku, sm.quantity_change,
                    sm.movement_type, sm.reference_id, sm.note, sm.created_at
             FROM stock_movements sm
             LEFT JOIN products p ON p.id = sm.product_id
             WHERE (?1 IS NULL OR sm.product_id = ?1)
               AND strftime('%Y-%m-%d', sm.created_at) >= ?2
               AND strftime('%Y-%m-%d', sm.created_at) <= ?3
             ORDER BY sm.created_at DESC LIMIT ?4"
        )
        .bind(product_id)
        .bind(from)
        .bind(to)
        .bind(limit)
        .fetch_all(pool)
        .await?;

        Ok(movements)
    }

    pub async fn get_sales_report_summary(
        pool: &SqlitePool,
        from_date: Option<String>,
        to_date: Option<String>,
        payment_method: Option<String>,
    ) -> Result<SalesReportSummary, AppError> {
        let from = from_date.unwrap_or_else(|| "1970-01-01".to_string());
        let to = to_date.unwrap_or_else(|| "2099-12-31".to_string());
        let pm = payment_method.filter(|m| !m.is_empty() && m != "ALL");
        let pm_norm = pm.as_deref().map(|m| {
            let u = m.to_uppercase();
            if u.contains("GIFT") || u == "GC" {
                "GIFT_CARD".to_string()
            } else {
                u
            }
        });

        let row = sqlx::query(
            "SELECT
                COALESCE(SUM(total_paise), 0) as total_revenue,
                COALESCE(SUM(discount_paise), 0) as total_discount,
                COALESCE(SUM(tax_paise), 0) as total_tax,
                COUNT(id) as total_count,
                COALESCE(SUM(CASE WHEN upper(payment_method) IN ('CASH', 'CH') THEN total_paise ELSE 0 END), 0) as cash_rev,
                COALESCE(SUM(CASE WHEN upper(payment_method) IN ('CARD', 'CP') THEN total_paise ELSE 0 END), 0) as card_rev,
                COALESCE(SUM(CASE WHEN upper(payment_method) IN ('UPI', 'UP') THEN total_paise ELSE 0 END), 0) as upi_rev,
                COALESCE(SUM(CASE WHEN upper(replace(payment_method, ' ', '_')) IN ('GIFT_CARD', 'GC') OR upper(payment_method) LIKE '%GIFT%' THEN total_paise ELSE 0 END), 0) as gift_card_rev
             FROM sales
             WHERE status IN ('PAID', 'PARTIALLY_PAID', 'LEGACY') AND created_at >= ? AND created_at <= ?
               AND (? IS NULL OR upper(replace(payment_method, ' ', '_')) = ? OR upper(payment_method) = ?)"
        )
        .bind(from)
        .bind(to)
        .bind(&pm_norm)
        .bind(&pm_norm)
        .bind(&pm_norm)
        .fetch_one(pool)
        .await?;

        let total_revenue_paise: i64 = row.get("total_revenue");
        let total_discount_paise: i64 = row.get("total_discount");
        let total_tax_paise: i64 = row.get("total_tax");
        let total_sales_count: i64 = row.get("total_count");
        let cash_revenue_paise: i64 = row.get("cash_rev");
        let card_revenue_paise: i64 = row.get("card_rev");
        let upi_revenue_paise: i64 = row.get("upi_rev");
        let gift_card_revenue_paise: i64 = row.get("gift_card_rev");

        Ok(SalesReportSummary {
            total_revenue_paise,
            total_discount_paise,
            total_tax_paise,
            total_sales_count,
            cash_revenue_paise,
            card_revenue_paise,
            upi_revenue_paise,
            gift_card_revenue_paise,
        })
    }

    pub async fn get_top_selling_products(
        pool: &SqlitePool,
        from_date: Option<String>,
        to_date: Option<String>,
        payment_method: Option<String>,
        limit: i64,
    ) -> Result<Vec<TopSellingProduct>, AppError> {
        let from = from_date.unwrap_or_else(|| "1970-01-01".to_string());
        let to = to_date.unwrap_or_else(|| "2099-12-31".to_string());
        let pm = payment_method.filter(|m| !m.is_empty() && m != "ALL");
        let pm_norm = pm.as_deref().map(|m| {
            let u = m.to_uppercase();
            if u.contains("GIFT") || u == "GC" {
                "GIFT_CARD".to_string()
            } else {
                u
            }
        });

        let rows = sqlx::query(
            "SELECT si.product_id, si.product_name,
                    p.sku as product_sku,
                    SUM(si.quantity) as total_quantity,
                    SUM(si.total_paise) as total_revenue_paise
             FROM sale_items si
             JOIN sales s ON s.id = si.sale_id
             LEFT JOIN products p ON p.id = si.product_id
             WHERE s.status IN ('PAID', 'PARTIALLY_PAID', 'LEGACY')
               AND s.created_at >= ? AND s.created_at <= ?
               AND (? IS NULL OR upper(replace(s.payment_method, ' ', '_')) = ? OR upper(s.payment_method) = ?)
             GROUP BY si.product_id, si.product_name
             ORDER BY total_quantity DESC LIMIT ?"
        )
        .bind(from)
        .bind(to)
        .bind(&pm_norm)
        .bind(&pm_norm)
        .bind(&pm_norm)
        .bind(limit)
        .fetch_all(pool)
        .await?;

        let items = rows
            .into_iter()
            .map(|r| TopSellingProduct {
                product_id: r.get("product_id"),
                product_name: r.get("product_name"),
                sku: r.get("product_sku"),
                total_quantity: r.get("total_quantity"),
                total_revenue_paise: r.get("total_revenue_paise"),
            })
            .collect();

        Ok(items)
    }

    pub async fn get_top_selling_categories(
        pool: &SqlitePool,
        from_date: Option<String>,
        to_date: Option<String>,
        payment_method: Option<String>,
        limit: i64,
    ) -> Result<Vec<TopSellingCategory>, AppError> {
        let from = from_date.unwrap_or_else(|| "1970-01-01".to_string());
        let to = to_date.unwrap_or_else(|| "2099-12-31".to_string());
        let pm = payment_method.filter(|m| !m.is_empty() && m != "ALL");
        let pm_norm = pm.as_deref().map(|m| {
            let u = m.to_uppercase();
            if u.contains("GIFT") || u == "GC" {
                "GIFT_CARD".to_string()
            } else {
                u
            }
        });

        let rows = sqlx::query(
            "SELECT
                COALESCE(c.id, 'uncategorized') as category_id,
                COALESCE(c.name, 'Uncategorized') as category_name,
                COUNT(DISTINCT si.product_id) as distinct_products_sold,
                COALESCE(SUM(si.quantity), 0) as total_quantity,
                COALESCE(SUM(si.total_paise), 0) as total_revenue_paise
             FROM sale_items si
             JOIN sales s ON s.id = si.sale_id
             LEFT JOIN products p ON p.id = si.product_id
             LEFT JOIN categories c ON c.id = p.category_id
             WHERE s.status IN ('PAID', 'PARTIALLY_PAID', 'LEGACY')
               AND s.created_at >= ? AND s.created_at <= ?
               AND (? IS NULL OR upper(replace(s.payment_method, ' ', '_')) = ? OR upper(s.payment_method) = ?)
             GROUP BY COALESCE(c.id, 'uncategorized'), COALESCE(c.name, 'Uncategorized')
             ORDER BY total_revenue_paise DESC LIMIT ?"
        )
        .bind(from)
        .bind(to)
        .bind(&pm_norm)
        .bind(&pm_norm)
        .bind(&pm_norm)
        .bind(limit)
        .fetch_all(pool)
        .await?;

        let items = rows
            .into_iter()
            .map(|r| TopSellingCategory {
                category_id: r.get("category_id"),
                category_name: r.get("category_name"),
                distinct_products_sold: r.get("distinct_products_sold"),
                total_quantity: r.get("total_quantity"),
                total_revenue_paise: r.get("total_revenue_paise"),
            })
            .collect();

        Ok(items)
    }

    pub async fn get_multi_year_sales(
        pool: &SqlitePool,
        years_back: i64,
        payment_method: Option<String>,
    ) -> Result<Vec<MultiYearSalesPoint>, AppError> {
        let pm = payment_method.filter(|m| !m.is_empty() && m != "ALL");
        let pm_norm = pm.as_deref().map(|m| {
            let u = m.to_uppercase();
            if u.contains("GIFT") || u == "GC" {
                "GIFT_CARD".to_string()
            } else {
                u
            }
        });

        let rows = sqlx::query_as::<_, MultiYearSalesPoint>(
            "SELECT
                strftime('%Y', s.created_at) as year,
                strftime('%m', s.created_at) as month,
                COALESCE(SUM(s.total_paise), 0) as total_revenue_paise,
                COUNT(s.id) as total_transactions
             FROM sales s
             WHERE s.status IN ('PAID', 'PARTIALLY_PAID', 'LEGACY')
               AND CAST(strftime('%Y', s.created_at) AS INTEGER) >= (CAST(strftime('%Y', 'now') AS INTEGER) - ?)
               AND (? IS NULL OR upper(replace(s.payment_method, ' ', '_')) = ? OR upper(s.payment_method) = ?)
             GROUP BY year, month
             ORDER BY year ASC, month ASC"
        )
        .bind(years_back)
        .bind(&pm_norm)
        .bind(&pm_norm)
        .bind(&pm_norm)
        .fetch_all(pool)
        .await?;

        Ok(rows)
    }

    pub async fn get_sales_breakdown(
        pool: &SqlitePool,
        period_type: &str,
        from_date: Option<String>,
        to_date: Option<String>,
    ) -> Result<Vec<SalesBreakdownItem>, AppError> {
        let is_today = period_type.eq_ignore_ascii_case("today");
        let today = chrono::Local::now().format("%Y-%m-%d").to_string();

        let from = if is_today {
            from_date
                .filter(|s| !s.trim().is_empty())
                .unwrap_or_else(|| today.clone())
        } else {
            from_date
                .filter(|s| !s.trim().is_empty())
                .unwrap_or_else(|| "1970-01-01".to_string())
        };

        let to = if is_today {
            to_date
                .filter(|s| !s.trim().is_empty())
                .unwrap_or_else(|| today.clone())
        } else {
            to_date
                .filter(|s| !s.trim().is_empty())
                .unwrap_or_else(|| "2099-12-31".to_string())
        };

        let items = match period_type.to_lowercase().as_str() {
            "monthly" => {
                sqlx::query_as::<_, SalesBreakdownItem>(
                    "SELECT
                        strftime('%Y-%m', created_at) as period,
                        COUNT(DISTINCT id) as total_transactions,
                        COALESCE(SUM(total_paise), 0) as total_sales_paise,
                        COALESCE(SUM(discount_paise), 0) as total_discount_paise,
                        COALESCE(SUM(CASE WHEN upper(payment_method) IN ('CASH', 'CH') THEN total_paise ELSE 0 END), 0) as cash_paise,
                        COALESCE(SUM(CASE WHEN upper(payment_method) IN ('UPI', 'UP') THEN total_paise ELSE 0 END), 0) as upi_paise,
                        COALESCE(SUM(CASE WHEN upper(payment_method) IN ('CARD', 'CP') THEN total_paise ELSE 0 END), 0) as card_paise,
                        COALESCE(SUM(CASE WHEN upper(replace(payment_method, ' ', '_')) IN ('GIFT_CARD', 'GC') OR upper(payment_method) LIKE '%GIFT%' THEN total_paise ELSE 0 END), 0) as gift_card_paise
                     FROM sales
                     WHERE status IN ('PAID', 'PARTIALLY_PAID', 'LEGACY')
                       AND strftime('%Y-%m-%d', created_at) >= ?
                       AND strftime('%Y-%m-%d', created_at) <= ?
                     GROUP BY period
                     ORDER BY period DESC"
                )
                .bind(&from)
                .bind(&to)
                .fetch_all(pool)
                .await?
            }
            "yearly" => {
                sqlx::query_as::<_, SalesBreakdownItem>(
                    "SELECT
                        strftime('%Y', created_at) as period,
                        COUNT(DISTINCT id) as total_transactions,
                        COALESCE(SUM(total_paise), 0) as total_sales_paise,
                        COALESCE(SUM(discount_paise), 0) as total_discount_paise,
                        COALESCE(SUM(CASE WHEN upper(payment_method) IN ('CASH', 'CH') THEN total_paise ELSE 0 END), 0) as cash_paise,
                        COALESCE(SUM(CASE WHEN upper(payment_method) IN ('UPI', 'UP') THEN total_paise ELSE 0 END), 0) as upi_paise,
                        COALESCE(SUM(CASE WHEN upper(payment_method) IN ('CARD', 'CP') THEN total_paise ELSE 0 END), 0) as card_paise,
                        COALESCE(SUM(CASE WHEN upper(replace(payment_method, ' ', '_')) IN ('GIFT_CARD', 'GC') OR upper(payment_method) LIKE '%GIFT%' THEN total_paise ELSE 0 END), 0) as gift_card_paise
                     FROM sales
                     WHERE status IN ('PAID', 'PARTIALLY_PAID', 'LEGACY')
                       AND strftime('%Y-%m-%d', created_at) >= ?
                       AND strftime('%Y-%m-%d', created_at) <= ?
                     GROUP BY period
                     ORDER BY period DESC"
                )
                .bind(&from)
                .bind(&to)
                .fetch_all(pool)
                .await?
            }
            _ => {
                sqlx::query_as::<_, SalesBreakdownItem>(
                    "SELECT
                        strftime('%Y-%m-%d', created_at) as period,
                        COUNT(DISTINCT id) as total_transactions,
                        COALESCE(SUM(total_paise), 0) as total_sales_paise,
                        COALESCE(SUM(discount_paise), 0) as total_discount_paise,
                        COALESCE(SUM(CASE WHEN upper(payment_method) IN ('CASH', 'CH') THEN total_paise ELSE 0 END), 0) as cash_paise,
                        COALESCE(SUM(CASE WHEN upper(payment_method) IN ('UPI', 'UP') THEN total_paise ELSE 0 END), 0) as upi_paise,
                        COALESCE(SUM(CASE WHEN upper(payment_method) IN ('CARD', 'CP') THEN total_paise ELSE 0 END), 0) as card_paise,
                        COALESCE(SUM(CASE WHEN upper(replace(payment_method, ' ', '_')) IN ('GIFT_CARD', 'GC') OR upper(payment_method) LIKE '%GIFT%' THEN total_paise ELSE 0 END), 0) as gift_card_paise
                     FROM sales
                     WHERE status IN ('PAID', 'PARTIALLY_PAID', 'LEGACY')
                       AND strftime('%Y-%m-%d', created_at) >= ?
                       AND strftime('%Y-%m-%d', created_at) <= ?
                     GROUP BY period
                     ORDER BY period DESC"
                )
                .bind(&from)
                .bind(&to)
                .fetch_all(pool)
                .await?
            }
        };

        Ok(items)
    }

    pub async fn get_product_flow(
        pool: &SqlitePool,
        product_id: Option<String>,
        from_date: Option<String>,
        to_date: Option<String>,
        limit: i64,
    ) -> Result<Vec<ProductFlowItem>, AppError> {
        let from = from_date
            .filter(|s| !s.trim().is_empty())
            .unwrap_or_else(|| "1970-01-01".to_string());
        let to = to_date
            .filter(|s| !s.trim().is_empty())
            .unwrap_or_else(|| "2099-12-31".to_string());

        let rows = sqlx::query_as::<_, ProductFlowItem>(
            "SELECT
                si.id as id,
                si.product_id as product_id,
                si.product_name as product_name,
                si.product_sku as product_sku,
                s.invoice_number as invoice_number,
                COALESCE(s.customer_name, 'Walk-in Customer') as customer_name,
                s.payment_method as payment_method,
                'SALE' as event_type,
                -si.quantity as quantity,
                si.unit_price_paise as unit_price_paise,
                si.total_paise as total_paise,
                s.created_at as created_at
             FROM sale_items si
             JOIN sales s ON s.id = si.sale_id
             WHERE s.status IN ('PAID', 'PARTIALLY_PAID', 'LEGACY')
               AND (?1 IS NULL OR si.product_id = ?1)
               AND strftime('%Y-%m-%d', s.created_at) >= ?2
               AND strftime('%Y-%m-%d', s.created_at) <= ?3
             ORDER BY s.created_at DESC
             LIMIT ?4"
        )
        .bind(product_id)
        .bind(from)
        .bind(to)
        .bind(limit)
        .fetch_all(pool)
        .await?;

        Ok(rows)
    }
}
