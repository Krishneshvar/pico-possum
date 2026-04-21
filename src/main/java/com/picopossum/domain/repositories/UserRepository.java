package com.picopossum.domain.repositories;

import com.picopossum.domain.model.User;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.UserFilter;

import java.util.Optional;

public interface UserRepository {
    PagedResult<User> findUsers(UserFilter filter);

    Optional<User> findUserById(long id);

    Optional<User> findUserByUsername(String username);

    User insertUser(User user);

    User updateUserById(long id, User userData);

    boolean softDeleteUser(long id);
}
