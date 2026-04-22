package com.picopossum.application.auth;

import com.picopossum.domain.model.User;
import com.picopossum.domain.repositories.UserRepository;
import com.picopossum.infrastructure.security.PasswordHasher;
import java.util.Optional;

public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public boolean anyUserExists() {
        return userRepository.findUsers(new com.picopossum.shared.dto.UserFilter(null, 1, 1, null, null)).totalCount() > 0;
    }

    public Optional<AuthUser> login(String username, String password) {
        return userRepository.findUserByUsername(username)
                .filter(User::active)
                .filter(user -> passwordHasher.verifyPassword(password, user.passwordHash()))
                .map(this::toAuthUser);
    }

    private AuthUser toAuthUser(User user) {
        return new AuthUser(user.id(), user.name(), user.username());
    }
}
