package com.possum.ui.common;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.auth.AuthorizationService;
import com.possum.application.auth.UserContext;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;

public class UIPermissionUtil {

    private static final AuthorizationService authService = new AuthorizationService();

    public static boolean hasPermission(String permission) {
        AuthUser currentUser = AuthContext.getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        UserContext userContext = new UserContext(
                currentUser.id(),
                currentUser.roles(),
                currentUser.permissions()
        );
        return authService.hasPermission(userContext, permission);
    }

    public static void requirePermission(Node node, String permission) {
        if (!hasPermission(permission)) {
            node.setVisible(false);
            node.setManaged(false);
        }
    }

    public static void requirePermission(MenuItem menuItem, String permission) {
        if (!hasPermission(permission)) {
            menuItem.setVisible(false);
        }
    }
}
