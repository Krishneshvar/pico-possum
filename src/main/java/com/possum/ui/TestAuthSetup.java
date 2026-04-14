package com.possum.ui;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;

import java.util.List;

public class TestAuthSetup {

    public static void setupMockAdminUser() {
        AuthUser adminUser = new AuthUser(
            1L,
            "Admin User",
            "admin",
            List.of("admin"),
            List.of("*")
        );
        AuthContext.setCurrentUser(adminUser);
    }

    public static void setupMockRegularUser() {
        AuthUser regularUser = new AuthUser(
            2L,
            "Regular User",
            "user",
            List.of("cashier"),
            List.of(
                "sales.create",
                "sales.view",
                "products.view",
                "inventory.view",
                "customers.view"
            )
        );
        AuthContext.setCurrentUser(regularUser);
    }

    public static void setupMockLimitedUser() {
        AuthUser limitedUser = new AuthUser(
            3L,
            "Limited User",
            "limited",
            List.of("viewer"),
            List.of("products.view", "inventory.view")
        );
        AuthContext.setCurrentUser(limitedUser);
    }

    public static void clearUser() {
        AuthContext.clear();
    }
}
