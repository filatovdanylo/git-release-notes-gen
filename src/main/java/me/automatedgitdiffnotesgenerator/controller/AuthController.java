package me.automatedgitdiffnotesgenerator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import me.automatedgitdiffnotesgenerator.dto.LoginRequest;
import me.automatedgitdiffnotesgenerator.dto.RegisterRequest;
import me.automatedgitdiffnotesgenerator.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register endpoint is accessible only for users with ADMIN role.\n" +
                    "Also if there are no users in the system yet, one can bootstrap a first admin by calling this endpoint",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<?> register(
            @RequestBody @Valid RegisterRequest registerRequest,
            Authentication authentication
    ) {
        try {
            var newUser = authService.register(registerRequest, authentication);

            return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getMessage());
        }
    }
}
