package me.automatedgitdiffnotesgenerator.controller;

import jakarta.validation.Valid;
import me.automatedgitdiffnotesgenerator.dto.LoginRequest;
import me.automatedgitdiffnotesgenerator.dto.RegisterRequest;
import me.automatedgitdiffnotesgenerator.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest registerRequest) {
        authService.register(registerRequest);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
