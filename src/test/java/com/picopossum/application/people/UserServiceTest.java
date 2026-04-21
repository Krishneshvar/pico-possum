package com.picopossum.application.people;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthUser;
import com.picopossum.domain.exceptions.AuthorizationException;
import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.User;
import com.picopossum.infrastructure.security.PasswordHasher;
import com.picopossum.domain.repositories.UserRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.UserFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordHasher passwordHasher;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordHasher);
        AuthContext.setCurrentUser(new AuthUser(1L, "Admin", "admin"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should fetch users with pagination")
    void getUsers_success() {
        UserFilter filter = new UserFilter(null, 1, 10, null, true);
        PagedResult<User> page = new PagedResult<>(List.of(), 0, 0, 1, 10);
        when(userRepository.findUsers(filter)).thenReturn(page);

        PagedResult<User> result = userService.getUsers(filter);

        assertNotNull(result);
        assertEquals(0, result.totalCount());
    }

    @Test
    @DisplayName("Should fetch user by ID")
    void getUserById_success() {
        User u = new User(1L, "Admin", "admin", "hash", true, LocalDateTime.now(), LocalDateTime.now(), null);
        when(userRepository.findUserById(1L)).thenReturn(Optional.of(u));

        Optional<User> result = userService.getUserById(1L);
        assertTrue(result.isPresent());
        assertEquals("Admin", result.get().name());
    }

    @Test
    @DisplayName("Should create user when valid")
    void createUser_success() {
        when(passwordHasher.hashPassword("secretpass")).thenReturn("hashed");
        when(userRepository.findUserByUsername("john_doe")).thenReturn(Optional.empty());
        User u = new User(1L, "John", "john_doe", "hashed", true, LocalDateTime.now(), LocalDateTime.now(), null);
        when(userRepository.insertUser(any(User.class))).thenReturn(u);

        User result = userService.createUser("John", "john_doe", "secretpass", true);
        assertEquals(1L, result.id());
        assertEquals("hashed", result.passwordHash());
    }

    @Test
    @DisplayName("Should block user creation if username is taken")
    void createUser_duplicateUsername_fail() {
        User existing = new User(2L, "Jane", "jane", "hash", true, LocalDateTime.now(), LocalDateTime.now(), null);
        when(userRepository.findUserByUsername("jane")).thenReturn(Optional.of(existing));

        assertThrows(ValidationException.class, () -> userService.createUser("Jane Doe", "jane", "secretpass", true));
    }

    @Test
    @DisplayName("Should throw validation error if username contains spaces")
    void createUser_invalidUsername_fail() {
        assertThrows(ValidationException.class, () -> userService.createUser("Jane", "ja ne", "secretpass", true));
    }

    @Test
    @DisplayName("Should throw validation error if password too short")
    void createUser_shortPassword_fail() {
        assertThrows(ValidationException.class, () -> userService.createUser("Jane", "jane123", "sec", true));
    }


    @Test
    @DisplayName("Should update user successfully")
    void updateUser_success() {
        User u = new User(1L, "Jane", "jane123", "hash", true, LocalDateTime.now(), LocalDateTime.now(), null);
        when(userRepository.findUserById(1L)).thenReturn(Optional.of(u));
        when(userRepository.updateUserById(eq(1L), any(User.class))).thenReturn(u);

        User result = userService.updateUser(1L, "Jane New", "jane123", "", true);
        assertEquals("Jane", result.name()); // Returns the mock
    }

    @Test
    @DisplayName("Should delete user successfully")
    void deleteUser_success() {
        when(userRepository.softDeleteUser(1L)).thenReturn(true);
        assertDoesNotThrow(() -> userService.deleteUser(1L));
    }

    @Test
    @DisplayName("Should throw NotFound if delete target missing")
    void deleteUser_notFound_fail() {
        when(userRepository.softDeleteUser(1L)).thenReturn(false);
        assertThrows(NotFoundException.class, () -> userService.deleteUser(1L));
    }
}
