package com.possum.application.auth;

import java.util.*;

public final class ConfigurableAuthorizationService {
    
    private final Map<String, RoleConfig> roleConfigs = new HashMap<>();
    private String superuserRole = "admin"; // Configurable
    
    public ConfigurableAuthorizationService() {
        initializeDefaultRoles();
    }
    
    private void initializeDefaultRoles() {
        // Admin role - full access
        roleConfigs.put("admin", new RoleConfig("admin", null, Integer.MAX_VALUE));
        
        // Manager role - inherits from cashier
        roleConfigs.put("manager", new RoleConfig("manager", "cashier", 100));
        
        // Cashier role - basic operations
        roleConfigs.put("cashier", new RoleConfig("cashier", null, 50));
        
        // Viewer role - read-only
        roleConfigs.put("viewer", new RoleConfig("viewer", null, 10));
    }
    
    public void setSuperuserRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("Superuser role cannot be null or empty");
        }
        this.superuserRole = roleName;
    }
    
    public String getSuperuserRole() {
        return superuserRole;
    }
    
    public boolean isSuperuser(UserContext user) {
        return user.roles().contains(superuserRole);
    }
    
    public boolean hasPermission(UserContext user, String permission) {
        if (isSuperuser(user)) {
            return true;
        }
        
        // Check direct permissions
        if (user.permissions().contains(permission)) {
            return true;
        }
        
        // Check inherited permissions through role hierarchy
        for (String role : user.roles()) {
            if (hasInheritedPermission(role, permission, user.permissions())) {
                return true;
            }
        }
        
        return false;
    }
    
    public boolean hasAnyPermission(UserContext user, List<String> permissions) {
        if (isSuperuser(user)) {
            return true;
        }
        return permissions.stream().anyMatch(p -> hasPermission(user, p));
    }
    
    public boolean hasAllPermissions(UserContext user, List<String> permissions) {
        if (isSuperuser(user)) {
            return true;
        }
        return permissions.stream().allMatch(p -> hasPermission(user, p));
    }
    
    public boolean hasRole(UserContext user, String role) {
        return user.roles().contains(role) || hasInheritedRole(user, role);
    }
    
    public boolean hasAnyRole(UserContext user, List<String> roles) {
        return roles.stream().anyMatch(r -> hasRole(user, r));
    }
    
    public boolean hasAllRoles(UserContext user, List<String> roles) {
        return roles.stream().allMatch(r -> hasRole(user, r));
    }
    
    public int getRolePriority(String role) {
        RoleConfig config = roleConfigs.get(role);
        return config != null ? config.priority : 0;
    }
    
    public boolean canDelegatePermission(UserContext delegator, String permission, UserContext delegatee) {
        // Can only delegate permissions you have
        if (!hasPermission(delegator, permission)) {
            return false;
        }
        
        // Cannot delegate to someone with higher role priority
        int delegatorPriority = getMaxRolePriority(delegator.roles());
        int delegateePriority = getMaxRolePriority(delegatee.roles());
        
        return delegatorPriority >= delegateePriority;
    }
    
    public Set<String> getEffectivePermissions(UserContext user) {
        if (isSuperuser(user)) {
            return Set.of("*"); // All permissions
        }
        
        Set<String> effective = new HashSet<>(user.permissions());
        
        // Add inherited permissions from role hierarchy
        for (String role : user.roles()) {
            effective.addAll(getInheritedPermissions(role, user.permissions()));
        }
        
        return effective;
    }
    
    public List<String> getRoleHierarchy(String role) {
        List<String> hierarchy = new ArrayList<>();
        String current = role;
        
        while (current != null) {
            hierarchy.add(current);
            RoleConfig config = roleConfigs.get(current);
            current = config != null ? config.parentRole : null;
        }
        
        return hierarchy;
    }
    
    private boolean hasInheritedRole(UserContext user, String targetRole) {
        for (String userRole : user.roles()) {
            List<String> hierarchy = getRoleHierarchy(userRole);
            if (hierarchy.contains(targetRole)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasInheritedPermission(String role, String permission, List<String> userPermissions) {
        List<String> hierarchy = getRoleHierarchy(role);
        // Check if any role in hierarchy would grant this permission
        // This is a simplified check - in production, you'd query role_permissions table
        return false;
    }
    
    private Set<String> getInheritedPermissions(String role, List<String> userPermissions) {
        // In production, this would query the database for all permissions
        // granted to roles in the hierarchy
        return new HashSet<>();
    }
    
    private int getMaxRolePriority(List<String> roles) {
        return roles.stream()
                .map(this::getRolePriority)
                .max(Integer::compareTo)
                .orElse(0);
    }
    
    public boolean isValidPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        // Format: module.action (e.g., sales.create, products.view)
        return permission.matches("^[a-z_]+\\.[a-z_]+$");
    }
    
    public boolean isValidRoleName(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        return role.matches("^[a-z_]+$");
    }
    
    private static class RoleConfig {
        final String name;
        final String parentRole;
        final int priority;
        
        RoleConfig(String name, String parentRole, int priority) {
            this.name = name;
            this.parentRole = parentRole;
            this.priority = priority;
        }
    }
}
