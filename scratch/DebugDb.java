import java.sql.*;
import java.math.BigDecimal;
import com.picopossum.domain.model.*;
import com.picopossum.persistence.repositories.sqlite.SqliteProductRepository;

public class DebugDb {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.createStatement().execute("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, description TEXT, category_id INTEGER, tax_rate REAL, sku TEXT, barcode TEXT, mrp REAL, cost_price REAL, stock_alert_cap INTEGER, status TEXT, image_path TEXT, created_at TEXT, updated_at TEXT, deleted_at TEXT)");
        
        SqliteProductRepository repo = new SqliteProductRepository(() -> conn);
        Product p = new Product(null, "Test", "Desc", null, null, BigDecimal.ZERO, "SKU1", null, BigDecimal.TEN, BigDecimal.TEN, 10, ProductStatus.ACTIVE, null, 0, null, null, null);
        
        try {
            repo.insertProduct(p);
            System.out.println("Insert OK");
            repo.findProductById(1L);
            System.out.println("Find OK");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
