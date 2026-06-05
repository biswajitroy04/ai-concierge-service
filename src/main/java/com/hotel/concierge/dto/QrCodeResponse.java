package com.hotel.concierge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrCodeResponse {

    private String qrCodeBase64;
    private String token;
    private String chatUrl;
    private Long expiresInSeconds;
    private String reservationId;
    private String guestName;
    private String guestPhone;
}
