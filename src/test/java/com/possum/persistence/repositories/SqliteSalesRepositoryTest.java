package com.possum.persistence.repositories;

import com.possum.domain.model.LegacySale;
import com.possum.domain.model.PaymentMethod;
import com.possum.domain.model.Sale;
import com.possum.domain.model.SaleItem;
import com.possum.domain.model.Transaction;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.repositories.sqlite.SqliteSalesRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.SaleFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SqliteSalesRepositoryTest {

    private ConnectionProvider connectionProvider;
    private Connection connection;
    private SqliteSalesRepository repository;

    @BeforeEach
    void setUp() {
        connectionProvider = mock(ConnectionProvider.class);
        connection = mock(Connection.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        repository = new SqliteSalesRepository(connectionProvider);
    }

    @Test
    void shouldInsertSale() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(stmt);
        when(stmt.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(1L);

        Sale sale = new Sale(null, "INV-001", null, new BigDecimal("100.00"),
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "paid", "fulfilled", 1L, 1L, null, null, null, null, null, null);

        long id = repository.insertSale(sale);

        assertEquals(1L, id);
        verify(stmt).setObject(1, "INV-001");
        verify(stmt).executeUpdate();
    }

    @Test
    void shouldInsertSaleItem() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(stmt);
        when(stmt.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(1L);

        SaleItem item = new SaleItem(null, 1L, 1L, "Variant", "SKU", "Product",
                2, new BigDecimal("50.00"), new BigDecimal("30.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "[]", BigDecimal.ZERO, 0);

        long id = repository.insertSaleItem(item);

        assertEquals(1L, id);
        verify(stmt).executeUpdate();
    }

    @Test
    void shouldFindSaleById() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("invoice_number")).thenReturn("INV-001");
        when(rs.getBigDecimal("total_amount")).thenReturn(new BigDecimal("100.00"));
        when(rs.getString("status")).thenReturn("paid");

        Optional<Sale> result = repository.findSaleById(1L);

        assertTrue(result.isPresent());
        assertEquals("INV-001", result.get().invoiceNumber());
        verify(stmt).setObject(1, 1L);
    }

    @Test
    void shouldFindSaleByInvoiceNumber() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("invoice_number")).thenReturn("INV-001");

        Optional<Sale> result = repository.findSaleByInvoiceNumber("INV-001");

        assertTrue(result.isPresent());
        verify(stmt).setObject(1, "INV-001");
    }

    @Test
    void shouldFindSaleBySequenceNumber() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);

        // First call: exact match fails
        // Second call: LIKE match succeeds
        when(rs.next())
                .thenReturn(false) // exact match
                .thenReturn(true).thenReturn(false); // LIKE match

        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("invoice_number")).thenReturn("S26CH0000001");

        Optional<Sale> result = repository.findSaleByInvoiceNumber("0000001");

        assertTrue(result.isPresent());
        assertEquals("S26CH0000001", result.get().invoiceNumber());
    }

    @Test
    void shouldFindSaleItems() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getLong("sale_id")).thenReturn(1L);
        when(rs.getLong("variant_id")).thenReturn(1L);
        when(rs.getInt("quantity")).thenReturn(2);
        when(rs.getBigDecimal("price_per_unit")).thenReturn(new BigDecimal("50.00"));

        List<SaleItem> result = repository.findSaleItems(1L);

        assertEquals(1, result.size());
        verify(stmt).setObject(1, 1L);
    }

    @Test
    void shouldFindTransactionsBySaleId() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getBigDecimal("amount")).thenReturn(new BigDecimal("100.00"));
        when(rs.getString("type")).thenReturn("payment");
        when(rs.getString("status")).thenReturn("completed");

        List<Transaction> result = repository.findTransactionsBySaleId(1L);

        assertEquals(1, result.size());
        verify(stmt).setObject(1, 1L);
    }

    @Test
    void shouldFindSalesWithFilter() throws SQLException {
        PreparedStatement countStmt = mock(PreparedStatement.class);
        PreparedStatement queryStmt = mock(PreparedStatement.class);
        ResultSet countRs = mock(ResultSet.class);
        ResultSet queryRs = mock(ResultSet.class);

        when(connection.prepareStatement(anyString()))
                .thenReturn(countStmt)
                .thenReturn(queryStmt);
        when(countStmt.executeQuery()).thenReturn(countRs);
        when(queryStmt.executeQuery()).thenReturn(queryRs);
        when(countRs.next()).thenReturn(true);
        when(countRs.getInt("count")).thenReturn(1);
        when(queryRs.next()).thenReturn(true).thenReturn(false);
        when(queryRs.getLong("id")).thenReturn(1L);
        when(queryRs.getString("invoice_number")).thenReturn("INV-001");
        when(queryRs.getBigDecimal("total_amount")).thenReturn(new BigDecimal("100.00"));
        when(queryRs.getString("status")).thenReturn("paid");

        SaleFilter filter = new SaleFilter(
                List.of("paid"), null, null, null, null, null,
                null, null, 1, 10, "sale_date", "DESC", null, null
        );
        PagedResult<Sale> result = repository.findSales(filter);

        assertEquals(1, result.totalCount());
        assertEquals(1, result.items().size());
    }

    @Test
    void shouldUpdateSaleStatus() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        int result = repository.updateSaleStatus(1L, "cancelled");

        assertEquals(1, result);
        verify(stmt).setObject(1, "cancelled");
        verify(stmt).setObject(2, 1L);
    }

    @Test
    void shouldUpdateFulfillmentStatus() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        int result = repository.updateFulfillmentStatus(1L, "fulfilled");

        assertEquals(1, result);
        verify(stmt).setObject(1, "fulfilled");
        verify(stmt).setObject(2, 1L);
    }

    @Test
    void shouldUpdateSalePaidAmount() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        int result = repository.updateSalePaidAmount(1L, new BigDecimal("50.00"));

        assertEquals(1, result);
        verify(stmt).setObject(1, new BigDecimal("50.00"));
        verify(stmt).setObject(2, 1L);
    }

    @Test
    void shouldInsertTransaction() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(stmt);
        when(stmt.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(1L);

        Transaction transaction = new Transaction(null, new BigDecimal("100.00"),
                "payment", 1L, null, "completed", null, null, null, null);

        long id = repository.insertTransaction(transaction, 1L);

        assertEquals(1L, id);
        verify(stmt).executeUpdate();
    }

    @Test
    void shouldGetLastSaleInvoiceNumber() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("invoice_number")).thenReturn("INV-999");

        Optional<String> result = repository.getLastSaleInvoiceNumber();

        assertTrue(result.isPresent());
        assertEquals("INV-999", result.get());
    }

    @Test
    void shouldFindPaymentMethods() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("name")).thenReturn("Cash");
        when(rs.getString("code")).thenReturn("CASH");
        when(rs.getInt("is_active")).thenReturn(1);

        List<PaymentMethod> result = repository.findPaymentMethods();

        assertEquals(1, result.size());
        assertEquals("Cash", result.get(0).name());
    }

    @Test
    void shouldCheckPaymentMethodExists() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("id")).thenReturn(1L);

        boolean result = repository.paymentMethodExists(1L);

        assertTrue(result);
        verify(stmt).setObject(1, 1L);
    }

    @Test
    void shouldCheckSaleExists() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("id")).thenReturn(1L);

        boolean result = repository.saleExists(1L);

        assertTrue(result);
    }

    @Test
    void shouldGetPaymentMethodCode() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("code")).thenReturn("CASH");

        Optional<String> result = repository.getPaymentMethodCode(1L);

        assertTrue(result.isPresent());
        assertEquals("CASH", result.get());
    }

    @Test
    void shouldGetNextSequenceForPaymentType() throws SQLException {
        PreparedStatement upsertStmt = mock(PreparedStatement.class);
        PreparedStatement selectStmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(connection.prepareStatement(anyString()))
                .thenReturn(upsertStmt)
                .thenReturn(selectStmt);
        when(upsertStmt.executeUpdate()).thenReturn(1);
        when(selectStmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("last_sequence")).thenReturn(42L);

        long result = repository.getNextSequenceForPaymentType("CASH");

        assertEquals(42L, result);
        verify(upsertStmt).setString(1, "CASH");
        verify(selectStmt).setString(1, "CASH");
    }

    @Test
    void shouldThrowWhenSequenceNotFound() throws SQLException {
        PreparedStatement upsertStmt = mock(PreparedStatement.class);
        PreparedStatement selectStmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(connection.prepareStatement(anyString()))
                .thenReturn(upsertStmt)
                .thenReturn(selectStmt);
        when(upsertStmt.executeUpdate()).thenReturn(1);
        when(selectStmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                repository.getNextSequenceForPaymentType("CASH")
        );
    }

    @Test
    void shouldUpdateTransactionPaymentMethod() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        int result = repository.updateTransactionPaymentMethod(1L, 2L);

        assertEquals(1, result);
        verify(stmt).setObject(1, 2L);
        verify(stmt).setObject(2, 1L);
    }

    @Test
    void shouldUpdateSaleCustomer() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        int result = repository.updateSaleCustomer(1L, 2L);

        assertEquals(1, result);
        verify(stmt).setObject(1, 2L);
        verify(stmt).setObject(2, 1L);
    }

    @Test
    void shouldDeleteSaleItem() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        int result = repository.deleteSaleItem(1L);

        assertEquals(1, result);
        verify(stmt).setObject(1, 1L);
    }

    @Test
    void shouldUpdateSaleItem() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        SaleItem item = new SaleItem(1L, 1L, 1L, "Variant", "SKU", "Product",
                3, new BigDecimal("60.00"), new BigDecimal("40.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "[]", BigDecimal.ZERO, 0);

        int result = repository.updateSaleItem(item);

        assertEquals(1, result);
        verify(stmt).setObject(10, 1L);
    }

    @Test
    void shouldUpdateSaleTotals() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        int result = repository.updateSaleTotals(1L, new BigDecimal("150.00"),
                new BigDecimal("15.00"), new BigDecimal("5.00"));

        assertEquals(1, result);
        verify(stmt).setObject(4, 1L);
    }

    @Test
    void shouldUpsertLegacySale() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        LegacySale legacySale = new LegacySale("INV-001", LocalDateTime.now(),
                "C001", "Customer", new BigDecimal("100.00"), 1L, "Cash", "import.csv");

        boolean result = repository.upsertLegacySale(legacySale);

        assertTrue(result);
        verify(stmt).executeUpdate();
    }
}
