package com.hotel.concierge.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;
    private final long qrExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.qr-expiration-ms}") long qrExpirationMs) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
        this.qrExpirationMs = qrExpirationMs;
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generateQrToken(Long reservationId, Long guestId, Long hotelId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + qrExpirationMs);

        return Jwts.builder()
                .subject("guest-" + guestId)
                .claims(Map.of(
                        "reservationId", reservationId,
                        "guestId", guestId,
                        "hotelId", hotelId,
                        "type", "QR_ACCESS"
                ))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getReservationIdFromQrToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("reservationId", Long.class);
    }

    public Long getGuestIdFromQrToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("guestId", Long.class);
    }

    public Long getHotelIdFromQrToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("hotelId", Long.class);
    }

    public boolean isQrToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "QR_ACCESS".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public long getQrExpirationMs() {
        return qrExpirationMs;
    }
}
