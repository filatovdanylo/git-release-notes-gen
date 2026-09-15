package me.automatedgitdiffnotesgenerator.security.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate;

    public CustomJwtAuthenticationConverter() {
        this.delegate = new JwtAuthenticationConverter();
        this.delegate.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return delegate.convert(jwt);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        String role = jwt.getClaimAsString("role");

        if (role == null || role.isBlank()) {
            return List.of();
        }

        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}