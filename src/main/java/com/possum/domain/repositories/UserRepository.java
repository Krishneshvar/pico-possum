package com.possum.domain.repositories;

import com.possum.domain.model.Permission;
import com.possum.domain.model.Role;
import com.possum.domain.model.User;
import com.possum.domain.model.UserPermissionOverride;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.UserFilter;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    PagedResult<User> findUsers(UserFilter filter);

    Optional<User> findUserById(long id);

    Optional<User> findUserByUsername(String username);

    User insertUserWithRoles(User user, List<Long> roleIds);

    User updateUserWithRolesById(long id, User userData, List<Long> roleIds);

    boolean softDeleteUser(long id);

    List<Role> getAllRoles();

    List<Permission> getAllPermissions();

    List<Role> getUserRoles(long userId);

    List<String> getUserPermissions(long userId);

    void assignUserRoles(long userId, List<Long> roleIds);

    List<UserPermissionOverride> getUserPermissionOverrides(long userId);

    void setUserPermission(long userId, long permissionId, boolean granted);

    List<Long> getRolePermissions(List<Long> roleIds);

    void revokeUserSessions(long userId);
}
