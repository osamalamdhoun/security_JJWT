package os.project.securitydemo2.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import os.project.securitydemo2.user.AppUser;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final SecretKey key;
    private final JwtParser parser;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.getExpiration().compareTo(java.time.Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("app.jwt.expiration must be at least 1s");
        }
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
        parser = Jwts.parser().verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .sig().clear().add(Jwts.SIG.HS256).and()
                .build();
    }

    public String createToken(AppUser user) {
        Instant now = Instant.now();
        // Claims are readable: never put passwords or sensitive data here.
        return Jwts.builder()
                .subject(user.getEmail())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getExpiration())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        // Verifies signature, algorithm, issuer and expiry before trusting claims.
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null || claims.getIssuedAt() == null
                || claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new JwtException("Missing required token claims");
        }
        return claims.getSubject();
    }

    public long expiresInSeconds() {
        return properties.getExpiration().toSeconds();
    }
}

