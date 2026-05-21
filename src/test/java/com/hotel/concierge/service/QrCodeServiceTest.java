package com.hotel.concierge.service;

import com.hotel.concierge.dto.QrCodeResponse;
import com.hotel.concierge.model.Guest;
import com.hotel.concierge.model.Hotel;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.repository.ReservationRepository;
import com.hotel.concierge.security.JwtTokenProvider;
import com.hotel.concierge.service.qr.QrCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrCodeServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private QrCodeService qrCodeService;

    private Reservation testReservation;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(qrCodeService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(qrCodeService, "qrWidth", 300);
        ReflectionTestUtils.setField(qrCodeService, "qrHeight", 300);

        Hotel hotel = Hotel.builder().id(1L).name("Test Hotel").build();
        Guest guest = Guest.builder().id(1L).firstName("John").lastName("Doe").title("Mr.").build();
        testReservation = Reservation.builder()
                .id(1L)
                .confirmationNumber("TEST-001")
                .hotel(hotel)
                .guest(guest)
                .roomNumber("101")
                .checkInDate(LocalDate.now())
                .checkOutDate(LocalDate.now().plusDays(3))
                .build();
    }

    @Test
    void generateQrCode_shouldReturnValidResponse() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(jwtTokenProvider.generateQrToken(anyLong(), anyLong(), anyLong())).thenReturn("test-token");
        when(jwtTokenProvider.getQrExpirationMs()).thenReturn(604800000L);

        QrCodeResponse response = qrCodeService.generateQrCode(1L);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("test-token");
        assertThat(response.getChatUrl()).contains("test-token");
        assertThat(response.getQrCodeBase64()).isNotBlank();
        assertThat(response.getGuestName()).contains("Doe");
    }

    @Test
    void generateQrCode_shouldThrowWhenReservationNotFound() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qrCodeService.generateQrCode(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Reservation not found");
    }

    @Test
    void validateQrToken_shouldReturnReservation() {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.isQrToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getReservationIdFromQrToken("valid-token")).thenReturn(1L);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        Reservation result = qrCodeService.validateQrToken("valid-token");

        assertThat(result).isNotNull();
        assertThat(result.getConfirmationNumber()).isEqualTo("TEST-001");
    }

    @Test
    void validateQrToken_shouldThrowForInvalidToken() {
        when(jwtTokenProvider.validateToken("invalid")).thenReturn(false);

        assertThatThrownBy(() -> qrCodeService.validateQrToken("invalid"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid or expired");
    }
}
