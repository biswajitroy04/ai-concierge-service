package com.hotel.concierge.service.ai.tools;

import com.hotel.concierge.model.*;
import com.hotel.concierge.repository.*;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * AI Tools for the concierge. Each instance is bound to a specific reservation.
 * Created per-session so the AI doesn't need to pass reservationId.
 */
@Slf4j
public class ConciergeTools {

    private final Long reservationId;
    private final String sessionId;
    private final HousekeepingRequestRepository housekeepingRepo;
    private final SpaBookingRepository spaBookingRepo;
    private final ReservationRepository reservationRepo;
    private final EscalationTicketRepository escalationRepo;
    private final ConversationRepository conversationRepo;
    private final RestaurantBookingRepository restaurantBookingRepo;

    public ConciergeTools(Long reservationId,
                          String sessionId,
                          HousekeepingRequestRepository housekeepingRepo,
                          SpaBookingRepository spaBookingRepo,
                          ReservationRepository reservationRepo,
                          EscalationTicketRepository escalationRepo,
                          ConversationRepository conversationRepo,
                          RestaurantBookingRepository restaurantBookingRepo) {
        this.reservationId = reservationId;
        this.sessionId = sessionId;
        this.housekeepingRepo = housekeepingRepo;
        this.spaBookingRepo = spaBookingRepo;
        this.reservationRepo = reservationRepo;
        this.escalationRepo = escalationRepo;
        this.conversationRepo = conversationRepo;
        this.restaurantBookingRepo = restaurantBookingRepo;
    }

    @Tool("Create a housekeeping request for the guest's room. Use for towels, cleaning, amenities, minibar, maintenance requests. Parameters: requestType (e.g. towels, cleaning, minibar), description, priority (LOW, NORMAL, HIGH, URGENT)")
    public String createHousekeepingRequest(String requestType, String description, String priority) {
        log.info("Creating housekeeping request: type={}, reservation={}", requestType, reservationId);

        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        HousekeepingRequest.RequestPriority prio;
        try {
            prio = HousekeepingRequest.RequestPriority.valueOf(priority.toUpperCase());
        } catch (Exception e) {
            prio = HousekeepingRequest.RequestPriority.NORMAL;
        }

        HousekeepingRequest request = HousekeepingRequest.builder()
                .reservation(reservation)
                .requestType(requestType)
                .description(description)
                .priority(prio)
                .status(HousekeepingRequest.RequestStatus.PENDING)
                .estimatedCompletionMinutes(getEstimatedTime(requestType))
                .build();

        housekeepingRepo.save(request);

        return String.format("Housekeeping request created successfully! Request #%d for '%s'. " +
                "Estimated completion: %d minutes. Our team will attend to Room %s shortly.",
                request.getId(), requestType, request.getEstimatedCompletionMinutes(),
                reservation.getRoomNumber());
    }

    @Tool("Book a spa appointment for the guest. Parameters: serviceName (e.g. Swedish Massage, Shiatsu, Deep Tissue Massage, Hot Stone Therapy, Facial Treatment, Aromatherapy, Couples Massage), preferredDate (YYYY-MM-DD format), preferredTime (HH:MM format, e.g. 19:00)")
    public String bookSpaAppointment(String serviceName, String preferredDate, String preferredTime) {
        log.info("Booking spa appointment: service={}, date={}, time={}, reservation={}", serviceName, preferredDate, preferredTime, reservationId);

        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        LocalDate bookingDate;
        try {
            bookingDate = LocalDate.parse(preferredDate);
        } catch (DateTimeParseException e) {
            // Try to interpret "today" or use current date
            bookingDate = LocalDate.now();
        }

        LocalTime startTime;
        try {
            startTime = LocalTime.parse(preferredTime);
        } catch (DateTimeParseException e) {
            // Try common formats
            try {
                startTime = LocalTime.parse(preferredTime.replace(" ", ""));
            } catch (Exception ex) {
                startTime = LocalTime.of(19, 0); // default 7 PM
            }
        }

        SpaBooking booking = SpaBooking.builder()
                .reservation(reservation)
                .serviceName(serviceName)
                .bookingDate(bookingDate)
                .startTime(startTime)
                .durationMinutes(getDuration(serviceName))
                .price(getPrice(serviceName))
                .status(SpaBooking.BookingStatus.CONFIRMED)
                .build();

        spaBookingRepo.save(booking);

        return String.format("Spa appointment confirmed! 🧖\n" +
                "Service: %s\nDate: %s\nTime: %s\nDuration: %d minutes\nPrice: $%.0f\n" +
                "Please arrive 15 minutes early. Booking #%d",
                serviceName, bookingDate, startTime,
                booking.getDurationMinutes(), booking.getPrice(), booking.getId());
    }

    @Tool("Check if the guest is eligible for late checkout based on loyalty status and hotel policy. Shows pricing tiers: until 2PM ($75, free for VIP/Platinum/Gold) or until 4PM ($150, subject to availability).")
    public String checkLateCheckoutEligibility() {
        log.info("Checking late checkout eligibility for reservation: {}", reservationId);

        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        Hotel hotel = reservation.getHotel();
        Guest guest = reservation.getGuest();

        if (reservation.getLateCheckoutApproved()) {
            return "Late checkout has already been approved for your stay! " +
                    "Your checkout time is " + reservation.getLateCheckoutTime() + ".";
        }

        boolean isFreeEligible = reservation.getIsVip()
                || "PLATINUM".equals(guest.getLoyaltyTier())
                || "GOLD".equals(guest.getLoyaltyTier());

        if (isFreeEligible) {
            return String.format("Great news! As a %s member, you're eligible for:\n" +
                    "• Late checkout until 2:00 PM — COMPLIMENTARY ✓\n" +
                    "• Late checkout until 4:00 PM — $150 (subject to availability)\n\n" +
                    "Which option would you prefer? Use confirmLateCheckout to confirm when the guest decides.",
                    guest.getLoyaltyTier() != null ? guest.getLoyaltyTier() : "VIP");
        } else {
            return "Late checkout options available:\n" +
                    "• Until 2:00 PM — $75\n" +
                    "• Until 4:00 PM — $150 (subject to availability)\n\n" +
                    "Standard checkout is 11:00 AM. Would you like to book one of these options? " +
                    "Use confirmLateCheckout to confirm when the guest decides.";
        }
    }

    @Tool("Confirm and record late checkout for the guest. Call this after the guest agrees. Parameters: checkoutTime (e.g. 14:00 or 16:00), fee (0 for complimentary, 75, or 150)")
    public String confirmLateCheckout(String checkoutTime, double fee) {
        log.info("Confirming late checkout for reservation: {}, time: {}, fee: {}", reservationId, checkoutTime, fee);

        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getLateCheckoutApproved()) {
            return "Late checkout was already confirmed. Checkout time: " + reservation.getLateCheckoutTime();
        }

        String time = checkoutTime != null ? checkoutTime : "14:00";
        reservation.setLateCheckoutApproved(true);
        reservation.setLateCheckoutTime(time);
        reservationRepo.save(reservation);

        String feeInfo = fee <= 0 ? "Complimentary (loyalty benefit)" : String.format("$%.0f (charged to room)", fee);

        return String.format("Late checkout confirmed! ✓\n" +
                "New checkout time: %s\nFee: %s\nRoom: %s\nConfirmation: %s",
                time, feeInfo, reservation.getRoomNumber(), reservation.getConfirmationNumber());
    }

    @Tool("Recommend nearby attractions, restaurants, and activities. Parameters: category (restaurants, attractions, nightlife, shopping), preferences (optional guest preferences)")
    public String recommendNearbyAttractions(String category, String preferences) {
        log.info("Generating recommendations: category={}, preferences={}", category, preferences);

        return switch (category.toLowerCase()) {
            case "restaurants" -> """
                    Here are top restaurant recommendations nearby:
                    🍽️ **The Golden Plate** - Fine dining, 0.3 miles - Michelin starred
                    🍣 **Sakura Sushi** - Japanese, 0.5 miles - Excellent omakase
                    🥩 **Prime Steakhouse** - American, 0.2 miles - Dry-aged steaks
                    🍝 **Trattoria Milano** - Italian, 0.4 miles - Authentic pasta
                    Would you like me to make a reservation at any of these?
                    """;
            case "attractions" -> """
                    Popular attractions near the hotel:
                    🏛️ **City Art Museum** - 0.8 miles - World-class exhibitions
                    🌳 **Central Park** - 0.3 miles - Beautiful walking trails
                    🛍️ **Luxury Shopping District** - 0.5 miles - Designer boutiques
                    🎭 **Grand Theater** - 1.0 mile - Broadway shows
                    Would you like more details or help with tickets?
                    """;
            case "nightlife" -> """
                    Evening entertainment options:
                    🍸 **Sky Lounge** - Rooftop bar, 0.1 miles - In our hotel!
                    🎵 **Jazz Corner** - Live music, 0.4 miles - Nightly performances
                    🍷 **Wine Cellar** - Wine bar, 0.3 miles - 500+ selections
                    Would you like me to arrange anything?
                    """;
            default -> """
                    I can recommend restaurants, attractions, nightlife, shopping, and wellness spots.
                    What type of experience are you looking for?
                    """;
        };
    }

    @Tool("Escalate the conversation to a human agent when the guest is frustrated, has a complex issue, or explicitly requests human assistance. Parameters: reason (why escalating), priority (LOW, MEDIUM, HIGH, CRITICAL)")
    public String escalateToHumanAgent(String reason, String priority) {
        log.warn("Escalating conversation to human agent: reason={}, session={}", reason, sessionId);

        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        Conversation conversation = conversationRepo.findBySessionId(sessionId).orElse(null);

        if (conversation != null) {
            EscalationTicket ticket = EscalationTicket.builder()
                    .conversation(conversation)
                    .reservation(reservation)
                    .reason(reason)
                    .priority(EscalationTicket.TicketPriority.valueOf(priority.toUpperCase()))
                    .status(EscalationTicket.TicketStatus.OPEN)
                    .build();

            escalationRepo.save(ticket);

            conversation.setIsEscalated(true);
            conversation.setStatus(Conversation.ConversationStatus.ESCALATED);
            conversation.setEscalationReason(reason);
            conversationRepo.save(conversation);
        }

        return "I've connected you with our guest services team. A team member will be with you shortly. " +
                "Your ticket priority is " + priority + ". Is there anything else I can note for them?";
    }

    @Tool("Book a restaurant reservation for the guest at one of the hotel restaurants. Parameters: restaurantName (e.g. The Grand Brasserie, Sky Lounge, Sakura), preferredDate (YYYY-MM-DD format), preferredTime (HH:MM format e.g. 21:00), partySize (number of guests), specialRequests (optional dietary or seating preferences)")
    public String bookRestaurantReservation(String restaurantName, String preferredDate, String preferredTime, int partySize, String specialRequests) {
        log.info("Booking restaurant: name={}, date={}, time={}, party={}, reservation={}", restaurantName, preferredDate, preferredTime, partySize, reservationId);

        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        LocalDate bookingDate;
        try {
            bookingDate = LocalDate.parse(preferredDate);
        } catch (DateTimeParseException e) {
            bookingDate = LocalDate.now();
        }

        LocalTime bookingTime;
        try {
            bookingTime = LocalTime.parse(preferredTime);
        } catch (DateTimeParseException e) {
            bookingTime = LocalTime.of(19, 0);
        }

        RestaurantBooking booking = RestaurantBooking.builder()
                .reservation(reservation)
                .restaurantName(restaurantName)
                .bookingDate(bookingDate)
                .bookingTime(bookingTime)
                .partySize(partySize > 0 ? partySize : 1)
                .specialRequests(specialRequests)
                .status(RestaurantBooking.BookingStatus.CONFIRMED)
                .build();

        restaurantBookingRepo.save(booking);

        return String.format("Restaurant reservation confirmed! 🍽️\n" +
                "Restaurant: %s\nDate: %s\nTime: %s\nParty size: %d\nBooking #%d\nReservation ID: %d\n" +
                "Please arrive on time. Enjoy your meal!",
                restaurantName, bookingDate, bookingTime, booking.getPartySize(), booking.getId(), reservationId);
    }

    private int getEstimatedTime(String requestType) {
        return switch (requestType.toLowerCase()) {
            case "towels", "amenities" -> 15;
            case "cleaning", "turndown" -> 30;
            case "maintenance" -> 45;
            case "minibar" -> 20;
            default -> 25;
        };
    }

    private int getDuration(String serviceName) {
        return switch (serviceName.toLowerCase()) {
            case "swedish massage", "deep tissue massage" -> 60;
            case "hot stone therapy" -> 75;
            case "facial treatment", "classic facial", "anti-aging facial", "hydrating facial", "men's facial" -> 45;
            case "aromatherapy", "shiatsu" -> 60;
            case "couples massage" -> 90;
            case "thai massage" -> 90;
            default -> 60;
        };
    }

    private double getPrice(String serviceName) {
        return switch (serviceName.toLowerCase()) {
            case "swedish massage" -> 150.0;
            case "deep tissue massage" -> 180.0;
            case "hot stone therapy" -> 200.0;
            case "facial treatment", "classic facial" -> 120.0;
            case "aromatherapy" -> 160.0;
            case "couples massage" -> 350.0;
            case "shiatsu" -> 170.0;
            case "thai massage" -> 220.0;
            case "sports recovery massage" -> 190.0;
            case "anti-aging facial" -> 180.0;
            case "hydrating facial" -> 140.0;
            case "men's facial" -> 130.0;
            default -> 150.0;
        };
    }
}
