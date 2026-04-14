package com.possum.persistence.mappers;

import com.possum.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserMapperTest {

    @Mock private ResultSet resultSet;
    private final UserMapper mapper = new UserMapper();

    @Test
    @DisplayName("Should map ResultSet to User properly")
    void mapUser_success() throws SQLException {
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getString("name")).thenReturn("System Admin");
        when(resultSet.getString("username")).thenReturn("admin");
        when(resultSet.getString("password_hash")).thenReturn("hash123");
        when(resultSet.getInt("is_active")).thenReturn(1);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getString("created_at")).thenReturn("2023-01-01 10:00:00");

        User user = mapper.map(resultSet);

        assertNotNull(user);
        assertEquals(1L, user.id());
        assertEquals("System Admin", user.name());
        assertEquals("admin", user.username());
        assertEquals("hash123", user.passwordHash());
        assertTrue(user.active());
        assertEquals(LocalDateTime.of(2023, 1, 1, 10, 0), user.createdAt());
    }
}
