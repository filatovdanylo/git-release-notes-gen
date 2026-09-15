package me.automatedgitdiffnotesgenerator.security.service;


import me.automatedgitdiffnotesgenerator.exception.ForbiddenException;
import me.automatedgitdiffnotesgenerator.security.config.JwtProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public String generateJwtToken(UserDetails userDetails) {
        Instant now = Instant.now();

        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .orElseThrow(() -> new ForbiddenException(
                        "User has no authorities; cannot issue a token without a role"));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .subject(userDetails.getUsername())
                .claim("role", role)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}