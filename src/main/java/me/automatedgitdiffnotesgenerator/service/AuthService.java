package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.dto.LoginRequest;
import me.automatedgitdiffnotesgenerator.dto.RegisterRequest;
import me.automatedgitdiffnotesgenerator.dto.TokenResponse;
import me.automatedgitdiffnotesgenerator.entity.User;
import me.automatedgitdiffnotesgenerator.exception.ConflictException;
import me.automatedgitdiffnotesgenerator.repository.UserRepository;
import me.automatedgitdiffnotesgenerator.security.config.JwtProperties;
import me.automatedgitdiffnotesgenerator.security.service.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String username = request.username().trim();
        var user = userRepository.findByUsername(username);

        if (user.isPresent()) {
            throw new ConflictException("Username is already in use");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        var newUser = new User(
                username,
                passwordHash,
                User.Role.USER,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        userRepository.save(newUser);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String username = request.username().trim();

        var authentication =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken
                                .unauthenticated(
                                        username,
                                        request.password()
                                )
                );

        return new TokenResponse(
                jwtService.generateJwtToken((UserDetails) authentication.getPrincipal()),
                "Bearer",
                jwtProperties.accessTokenTtl().toMillis()
        );
    }
}
