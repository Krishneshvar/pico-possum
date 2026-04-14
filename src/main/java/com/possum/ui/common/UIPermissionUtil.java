package com.possum.ui.common;

import javafx.scene.Node;
import javafx.scene.control.MenuItem;

public class UIPermissionUtil {

    public static boolean hasPermission(String permission) {
        return true;
    }

    public static void requirePermission(Node node, String permission) {
        // No-op for single-user
    }

    public static void requirePermission(MenuItem menuItem, String permission) {
        // No-op for single-user
    }
}
