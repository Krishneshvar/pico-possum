package com.possum.application.people;

import com.possum.domain.model.Permission;
import com.possum.domain.model.Role;
import com.possum.domain.model.User;
import com.possum.domain.model.UserPermissionOverride;
import com.possum.infrastructure.security.PasswordHasher;
import com.possum.domain.repositories.UserRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.UserFilter;
import com.possum.shared.util.DomainValidators;

import com.possum.shared.util.TimeUtil;
import java.util.List;
import java.util.Optional;

public class UserService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public PagedResult<User> getUsers(UserFilter filter) {
        return userRepository.findUsers(filter);
    }

    public Optional<User> getUserById(long id) {
        return userRepository.findUserById(id);
    }

    public User createUser(String name, String username, String password, boolean active, List<Long> roleIds) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.USERS_MANAGE);
        if (name == null || name.isBlank()) throw new com.possum.domain.exceptions.ValidationException("User name is required");
        if (username == null || username.isBlank()) throw new com.possum.domain.exceptions.ValidationException("Username is required");
        if (username.contains(" ")) throw new com.possum.domain.exceptions.ValidationException("Username cannot contain spaces");
        if (username.length() < DomainValidators.MIN_USERNAME_LENGTH) throw new com.possum.domain.exceptions.ValidationException("Username must be at least " + DomainValidators.MIN_USERNAME_LENGTH + " characters");
        if (password == null || password.isBlank()) throw new com.possum.domain.exceptions.ValidationException("Password is required");
        if (password.length() < DomainValidators.MIN_PASSWORD_LENGTH) throw new com.possum.domain.exceptions.ValidationException("Password must be at least " + DomainValidators.MIN_PASSWORD_LENGTH + " characters");
        if (userRepository.findUserByUsername(username.trim()).isPresent())
            throw new com.possum.domain.exceptions.ValidationException("Username '" + username.trim() + "' is already taken");
        
        String hashedPassword = passwordHasher.hashPassword(password);
        User newUser = new User(null, name, username, hashedPassword, active, TimeUtil.nowUTC(), TimeUtil.nowUTC(), null);
        return userRepository.insertUserWithRoles(newUser, roleIds);
    }

    public User updateUser(long id, String name, String username, String password, boolean active, List<Long> roleIds) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.USERS_MANAGE);
        if (name == null || name.isBlank()) throw new com.possum.domain.exceptions.ValidationException("User name is required");
        if (username == null || username.isBlank()) throw new com.possum.domain.exceptions.ValidationException("Username is required");
        User existingUser = userRepository.findUserById(id)
                .orElseThrow(() -> new com.possum.domain.exceptions.NotFoundException("User not found: " + id));
        
        String hashedPassword = existingUser.passwordHash();
        if (password != null && !password.trim().isEmpty()) {
            hashedPassword = passwordHasher.hashPassword(password);
        }

        User updatedUser = new User(existingUser.id(), name, username, hashedPassword, active, existingUser.createdAt(), TimeUtil.nowUTC(), existingUser.deletedAt());
        User result = userRepository.updateUserWithRolesById(id, updatedUser, roleIds);
        
        if (!active) {
            userRepository.revokeUserSessions(id);
        }
        return result;
    }

    public void deleteUser(long id) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.USERS_MANAGE);
        if (!userRepository.softDeleteUser(id)) {
            throw new com.possum.domain.exceptions.NotFoundException("User not found: " + id);
        }
    }

    public List<Role> getAllRoles() {
        return userRepository.getAllRoles();
    }

    public List<Permission> getAllPermissions() {
        return userRepository.getAllPermissions();
    }

    public List<Role> getUserRoles(long userId) {
        return userRepository.getUserRoles(userId);
    }

    public List<String> getUserPermissions(long userId) {
        return userRepository.getUserPermissions(userId);
    }

    public void assignUserRoles(long userId, List<Long> roleIds) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.USERS_MANAGE);
        userRepository.assignUserRoles(userId, roleIds);
    }

    public List<UserPermissionOverride> getUserPermissionOverrides(long userId) {
        return userRepository.getUserPermissionOverrides(userId);
    }

    public void setUserPermission(long userId, long permissionId, boolean granted) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.USERS_MANAGE);
        userRepository.setUserPermission(userId, permissionId, granted);
    }

    public List<Long> getRolePermissions(List<Long> roleIds) {
        return userRepository.getRolePermissions(roleIds);
    }
}
