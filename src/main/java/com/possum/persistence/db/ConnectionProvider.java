package com.possum.persistence.db;

import java.sql.Connection;

public interface ConnectionProvider {
    Connection getConnection();
}
