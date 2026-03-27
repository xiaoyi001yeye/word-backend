package com.example.words.service;

import com.example.words.dto.LoginRequest;
import com.example.words.dto.LoginResponse;
import com.example.words.dto.QuoteResponse;
import com.example.words.dto.UserResponse;
import com.example.words.model.AppUser;
import com.example.words.repository.AppUserRepository;
import com.example.words.security.AuthenticatedUser;
import com.example.words.security.JwtService;
import java.time.LocalDateTime;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final FamousQuoteService famousQuoteService;

    public AuthService(
            AuthenticationManager authenticationManager,
            AppUserRepository appUserRepository,
            JwtService jwtService,
            CurrentUserService currentUserService,
            FamousQuoteService famousQuoteService) {
        this.authenticationManager = authenticationManager;
        this.appUserRepository = appUserRepository;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
        this.famousQuoteService = famousQuoteService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        AppUser user = appUserRepository.findById(principal.getId())
                .orElseThrow(() -> new com.example.words.exception.ResourceNotFoundException(
                        "User not found: " + principal.getId()));
        user.setLastLoginAt(LocalDateTime.now());
        appUserRepository.save(user);

        return new LoginResponse(
                jwtService.generateToken(AuthenticatedUser.from(user)),
                UserResponse.from(user),
                famousQuoteService.getRandomQuote()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(currentUserService.getCurrentUser());
    }

    @Transactional(readOnly = true)
    public QuoteResponse getLoginQuote() {
        return famousQuoteService.getRandomQuote();
    }
}
