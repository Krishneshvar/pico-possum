package com.picopossum.application.people;

import com.picopossum.domain.model.User;
import com.picopossum.infrastructure.security.PasswordHasher;
import com.picopossum.domain.repositories.UserRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.UserFilter;
import com.picopossum.shared.util.DomainValidators;

import com.picopossum.shared.util.TimeUtil;
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

    public User createUser(String name, String username, String password, boolean active) {
        if (name == null || name.isBlank()) throw new com.picopossum.domain.exceptions.ValidationException("User name is required");
        if (username == null || username.isBlank()) throw new com.picopossum.domain.exceptions.ValidationException("Username is required");
        if (username.contains(" ")) throw new com.picopossum.domain.exceptions.ValidationException("Username cannot contain spaces");
        if (username.length() < DomainValidators.MIN_USERNAME_LENGTH) throw new com.picopossum.domain.exceptions.ValidationException("Username must be at least " + DomainValidators.MIN_USERNAME_LENGTH + " characters");
        if (password == null || password.isBlank()) throw new com.picopossum.domain.exceptions.ValidationException("Password is required");
        if (password.length() < DomainValidators.MIN_PASSWORD_LENGTH) throw new com.picopossum.domain.exceptions.ValidationException("Password must be at least " + DomainValidators.MIN_PASSWORD_LENGTH + " characters");
        if (userRepository.findUserByUsername(username.trim()).isPresent())
            throw new com.picopossum.domain.exceptions.ValidationException("Username '" + username.trim() + "' is already taken");
        
        String hashedPassword = passwordHasher.hashPassword(password);
        User newUser = new User(null, name, username, hashedPassword, active, TimeUtil.nowUTC(), TimeUtil.nowUTC(), null);
        return userRepository.insertUser(newUser);
    }

    public User updateUser(long id, String name, String username, String password, boolean active) {
        if (name == null || name.isBlank()) throw new com.picopossum.domain.exceptions.ValidationException("User name is required");
        if (username == null || username.isBlank()) throw new com.picopossum.domain.exceptions.ValidationException("Username is required");
        User existingUser = userRepository.findUserById(id)
                .orElseThrow(() -> new com.picopossum.domain.exceptions.NotFoundException("User not found: " + id));
        
        String hashedPassword = existingUser.passwordHash();
        if (password != null && !password.trim().isEmpty()) {
            hashedPassword = passwordHasher.hashPassword(password);
        }

        User updatedUser = new User(existingUser.id(), name, username, hashedPassword, active, existingUser.createdAt(), TimeUtil.nowUTC(), existingUser.deletedAt());
        User result = userRepository.updateUserById(id, updatedUser);
        
        if (!active) {
            userRepository.revokeUserSessions(id);
        }
        return result;
    }

    public void deleteUser(long id) {
        if (!userRepository.softDeleteUser(id)) {
            throw new com.picopossum.domain.exceptions.NotFoundException("User not found: " + id);
        }
    }
}
