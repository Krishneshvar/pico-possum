package com.possum.application.auth;

import java.util.List;

public class AuthorizationService {

    private final ConfigurableAuthorizationService configurableService;

    public AuthorizationService() {
        this.configurableService = new ConfigurableAuthorizationService();
    }
    
    public AuthorizationService(ConfigurableAuthorizationService configurableService) {
        this.configurableService = configurableService;
    }

    public boolean isAdmin(UserContext user) {
        return configurableService.isSuperuser(user);
    }

    public boolean hasPermission(UserContext user, String permission) {
        return configurableService.hasPermission(user, permission);
    }

    public boolean hasAnyPermission(UserContext user, List<String> permissions) {
        return configurableService.hasAnyPermission(user, permissions);
    }

    public boolean hasAllPermissions(UserContext user, List<String> permissions) {
        return configurableService.hasAllPermissions(user, permissions);
    }

    public boolean hasRole(UserContext user, String role) {
        return configurableService.hasRole(user, role);
    }

    public boolean hasAnyRole(UserContext user, List<String> roles) {
        return configurableService.hasAnyRole(user, roles);
    }

    public boolean hasAllRoles(UserContext user, List<String> roles) {
        return configurableService.hasAllRoles(user, roles);
    }

    public boolean isValidPermission(String permission) {
        return configurableService.isValidPermission(permission);
    }

    public boolean isValidRoleName(String role) {
        return configurableService.isValidRoleName(role);
    }
    
    public void setSuperuserRole(String roleName) {
        configurableService.setSuperuserRole(roleName);
    }
    
    public String getSuperuserRole() {
        return configurableService.getSuperuserRole();
    }
}
