package com.hotel.concierge.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        String secret = "YWktY29uY2llcmdlLXNlcnZpY2Utc2VjcmV0LWtleS1mb3ItaG90ZWwtcGxhdGZvcm0tMjAyNA==";
        tokenProvider = new JwtTokenProvider(secret, 86400000L, 604800000L);
    }

    @Test
    void generateToken_shouldCreateValidToken() {
        String token = tokenProvider.generateToken("admin", "ADMIN");

        assertThat(token).isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("admin");
    }

    @Test
    void generateQrToken_shouldContainReservationInfo() {
        String token = tokenProvider.generateQrToken(1L, 2L, 3L);

        assertThat(token).isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.isQrToken(token)).isTrue();
        assertThat(tokenProvider.getReservationIdFromQrToken(token)).isEqualTo(1L);
        assertThat(tokenProvider.getGuestIdFromQrToken(token)).isEqualTo(2L);
        assertThat(tokenProvider.getHotelIdFromQrToken(token)).isEqualTo(3L);
    }

    @Test
    void validateToken_shouldRejectInvalidToken() {
        assertThat(tokenProvider.validateToken("invalid.token.here")).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.validateToken(null)).isFalse();
    }

    @Test
    void isQrToken_shouldDistinguishTokenTypes() {
        String adminToken = tokenProvider.generateToken("admin", "ADMIN");
        String qrToken = tokenProvider.generateQrToken(1L, 1L, 1L);

        assertThat(tokenProvider.isQrToken(adminToken)).isFalse();
        assertThat(tokenProvider.isQrToken(qrToken)).isTrue();
    }
}
