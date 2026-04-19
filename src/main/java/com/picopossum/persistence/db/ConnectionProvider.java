package com.picopossum.persistence.db;

import java.sql.Connection;

public interface ConnectionProvider {
    Connection getConnection();
}
