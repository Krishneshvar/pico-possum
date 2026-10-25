use chrono::Datelike;

pub struct BusinessRules;

impl BusinessRules {
    pub fn payment_method_code(payment_method: &str) -> &'static str {
        match payment_method.to_uppercase().as_str() {
            "CASH" | "CH" => "CH",
            "CARD" | "CP" => "CP",
            "UPI" | "UP" => "UP",
            "GIFT_CARD" | "GC" => "GC",
            _ => "CH",
        }
    }

    pub fn generate_invoice_number(payment_method: &str, sequence: i64) -> String {
        let now = chrono::Local::now();
        let yy = now.year() % 100;
        let code = Self::payment_method_code(payment_method);
        format!("S{:02}{}{:07}", yy, code, sequence)
    }

    pub fn format_paise(paise: i64, symbol: &str) -> String {
        let rupees = paise as f64 / 100.0;
        format!("{}{:.2}", symbol, rupees)
    }
}
