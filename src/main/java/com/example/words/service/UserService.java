package com.example.words.service;

import com.example.words.dto.CreateUserRequest;
import com.example.words.dto.UpdateUserRoleRequest;
import com.example.words.dto.UpdateUserStatusRequest;
import com.example.words.dto.UserResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.UserStatus;
import com.example.words.repository.AppUserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String normalizedUsername = request.getUsername().trim().toLowerCase(Locale.ROOT);
        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new BadRequestException("Username already exists: " + normalizedUsername);
        }

        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName().trim());
        user.setEmail(trimToNull(request.getEmail()));
        user.setPhone(trimToNull(request.getPhone()));
        user.setRole(request.getRole());
        user.setStatus(UserStatus.ACTIVE);
        return UserResponse.from(appUserRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return appUserRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(getUserEntity(id));
    }

    @Transactional
    public UserResponse updateRole(Long id, UpdateUserRoleRequest request) {
        AppUser user = getUserEntity(id);
        user.setRole(request.getRole());
        return UserResponse.from(appUserRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        AppUser user = getUserEntity(id);
        user.setStatus(request.getStatus());
        return UserResponse.from(appUserRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AppUser getUserEntity(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
