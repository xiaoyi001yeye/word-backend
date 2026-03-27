package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.LoginRequest;
import com.example.words.dto.LoginResponse;
import com.example.words.dto.QuoteResponse;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.FamousQuoteRepository;
import com.example.words.security.AuthenticatedUser;
import com.example.words.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private FamousQuoteRepository famousQuoteRepository;

    @Mock
    private Authentication authentication;

    private AuthService authService;
    private JwtService jwtService;
    private FamousQuoteService famousQuoteService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                24
        );
        famousQuoteService = new FamousQuoteService(famousQuoteRepository);
        authService = new AuthService(
                authenticationManager,
                appUserRepository,
                jwtService,
                null,
                famousQuoteService
        );
    }

    @Test
    void loginShouldReturnRandomQuoteAndUpdateLastLoginAt() {
        LoginRequest request = new LoginRequest("alice", "password");
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("alice");
        user.setPasswordHash("encoded-password");
        user.setDisplayName("Alice");
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);

        AuthenticatedUser principal = AuthenticatedUser.from(user);
        QuoteResponse quoteResponse = new QuoteResponse(
                "The secret of getting ahead is getting started.",
                "领先一步的秘诀，就是立刻开始。",
                "Mark Twain"
        );

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(famousQuoteRepository.findRandomQuote()).thenReturn(Optional.of(
                new com.example.words.model.FamousQuote(
                        1L,
                        quoteResponse.getText(),
                        quoteResponse.getTranslation(),
                        quoteResponse.getAuthor(),
                        null
                )
        ));

        LoginResponse response = authService.login(request);

        assertNotNull(response.getToken());
        assertEquals("alice", response.getUser().getUsername());
        assertEquals(quoteResponse, response.getQuote());
        assertNotNull(user.getLastLoginAt());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(appUserRepository).save(user);
        verify(famousQuoteRepository).findRandomQuote();
    }
}
