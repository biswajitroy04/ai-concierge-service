package com.hotel.concierge.service.ai.tools;

import com.hotel.concierge.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory to create per-session ConciergeTools instances bound to a specific reservation.
 */
@Component
@RequiredArgsConstructor
public class ConciergeToolsFactory {

    private final HousekeepingRequestRepository housekeepingRepo;
    private final SpaBookingRepository spaBookingRepo;
    private final ReservationRepository reservationRepo;
    private final EscalationTicketRepository escalationRepo;
    private final ConversationRepository conversationRepo;
    private final RestaurantBookingRepository restaurantBookingRepo;

    public ConciergeTools createForSession(Long reservationId, String sessionId) {
        return new ConciergeTools(
                reservationId,
                sessionId,
                housekeepingRepo,
                spaBookingRepo,
                reservationRepo,
                escalationRepo,
                conversationRepo,
                restaurantBookingRepo
        );
    }
}
