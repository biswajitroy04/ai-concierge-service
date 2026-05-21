package com.hotel.concierge.service.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.hotel.concierge.dto.QrCodeResponse;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.repository.ReservationRepository;
import com.hotel.concierge.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeService {

    private final JwtTokenProvider jwtTokenProvider;
    private final ReservationRepository reservationRepository;

    @Value("${qr.base-url}")
    private String baseUrl;

    @Value("${qr.width}")
    private int qrWidth;

    @Value("${qr.height}")
    private int qrHeight;

    public QrCodeResponse generateQrCode(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));

        // Generate JWT token for QR code
        String token = jwtTokenProvider.generateQrToken(
                reservation.getId(),
                reservation.getGuest().getId(),
                reservation.getHotel().getId()
        );

        // Build chat URL
        String chatUrl = baseUrl + "/chat/guest?token=" + token;

        // Generate QR code image
        String qrCodeBase64 = generateQrImage(chatUrl);

        long expiresInSeconds = jwtTokenProvider.getQrExpirationMs() / 1000;

        return QrCodeResponse.builder()
                .qrCodeBase64(qrCodeBase64)
                .token(token)
                .chatUrl(chatUrl)
                .expiresInSeconds(expiresInSeconds)
                .reservationId(reservation.getConfirmationNumber())
                .guestName(reservation.getGuest().getFullName())
                .build();
    }

    public Reservation validateQrToken(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("Invalid or expired QR token");
        }

        if (!jwtTokenProvider.isQrToken(token)) {
            throw new RuntimeException("Token is not a valid QR access token");
        }

        Long reservationId = jwtTokenProvider.getReservationIdFromQrToken(token);
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found for token"));
    }

    private String generateQrImage(String content) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.MARGIN, 2,
                    EncodeHintType.CHARACTER_SET, "UTF-8"
            );

            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, qrWidth, qrHeight, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("Failed to generate QR code: {}", e.getMessage());
            throw new RuntimeException("QR code generation failed", e);
        }
    }
}
