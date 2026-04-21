package com.picopossum.persistence.repositories;

import com.picopossum.domain.model.Sale;
import com.picopossum.domain.model.SaleItem;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.persistence.repositories.sqlite.SqliteSalesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.*;
import java.util.List;

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
    @DisplayName("Should insert a Sale record and return generated ID")
    void shouldInsertSale() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(stmt);
        when(stmt.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(1L);

        Sale sale = new Sale(null, "INV-001", null, new BigDecimal("100.00"),
                new BigDecimal("100.00"), BigDecimal.ZERO,
                "paid", "fulfilled", null, "Guest", null, null, "System", 1L, "Cash", "INV-001");

        long id = repository.insertSale(sale);

        assertEquals(1L, id);
        verify(stmt).setObject(1, "INV-001");
        verify(stmt).executeUpdate();
    }

    @Test
    @DisplayName("Should insert a SaleItem record")
    void shouldInsertSaleItem() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(stmt);
        when(stmt.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(1L);

        SaleItem item = new SaleItem(null, 1L, 1L, "SKU", "Product",
                2, new BigDecimal("50.00"), new BigDecimal("30.00"),
                BigDecimal.ZERO, 0);

        long id = repository.insertSaleItem(item);

        assertEquals(1L, id);
        verify(stmt).executeUpdate();
    }

    @Test
    @DisplayName("Should query all items for a given sale ID")
    void shouldFindSaleItems() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getLong("sale_id")).thenReturn(1L);
        when(rs.getLong("product_id")).thenReturn(1L);
        when(rs.getString("sku")).thenReturn("SKU");
        when(rs.getString("product_name")).thenReturn("Product");
        when(rs.getInt("quantity")).thenReturn(2);
        when(rs.getObject("price_per_unit")).thenReturn(new BigDecimal("50.00"));
        when(rs.getObject("cost_per_unit")).thenReturn(new BigDecimal("30.00"));
        when(rs.getObject("discount_amount")).thenReturn(BigDecimal.ZERO);
        when(rs.getInt("returned_quantity")).thenReturn(0);

        List<SaleItem> result = repository.findSaleItems(1L);

        assertEquals(1, result.size());
        verify(stmt).setObject(1, 1L);
    }

    @Test
    @DisplayName("Should update an existing SaleItem record")
    void shouldUpdateSaleItem() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        SaleItem item = new SaleItem(1L, 1L, 1L, "SKU", "Product",
                3, new BigDecimal("60.00"), new BigDecimal("40.00"),
                BigDecimal.ZERO, 0);

        int result = repository.updateSaleItem(item);
        assertEquals(1, result);
        verify(stmt, atLeastOnce()).setObject(anyInt(), any());
    }
}
