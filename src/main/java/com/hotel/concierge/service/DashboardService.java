package com.hotel.concierge.service;

import com.hotel.concierge.dto.DashboardStats;
import com.hotel.concierge.model.Conversation;
import com.hotel.concierge.model.EscalationTicket;
import com.hotel.concierge.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ConversationRepository conversationRepo;
    private final EscalationTicketRepository escalationRepo;
    private final HousekeepingRequestRepository housekeepingRepo;

    @Transactional(readOnly = true)
    public DashboardStats getStats(Long hotelId) {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        long activeConversations = conversationRepo.countActiveByHotel(hotelId);
        long totalToday = conversationRepo.countConversationsSince(hotelId, todayStart);
        long pendingEscalations = escalationRepo.countOpenByHotel(hotelId);
        long housekeepingPending = housekeepingRepo.countPendingByHotel(hotelId);
        Double avgSentiment = conversationRepo.averageSentimentByHotel(hotelId);

        List<Conversation> activeConvos = conversationRepo.findActiveByHotel(hotelId);
        List<Conversation> allConvosWithSentiment = conversationRepo.findAllWithSentimentByHotel(hotelId);
        List<EscalationTicket> activeEscalations = escalationRepo.findActiveByHotel(hotelId);

        // Build sentiment distribution from ALL conversations (not just active)
        Map<String, Long> sentimentDist = allConvosWithSentiment.stream()
                .collect(Collectors.groupingBy(Conversation::getSentimentLabel, Collectors.counting()));

        // Build conversation summaries
        List<DashboardStats.ConversationSummary> recentConvos = activeConvos.stream()
                .limit(10)
                .map(c -> DashboardStats.ConversationSummary.builder()
                        .sessionId(c.getSessionId())
                        .guestName(c.getReservation().getGuest().getFullName())
                        .roomNumber(c.getReservation().getRoomNumber())
                        .sentiment(c.getSentimentLabel())
                        .status(c.getStatus().name())
                        .startedAt(c.getStartedAt() != null ? c.getStartedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "")
                        .build())
                .toList();

        // Build escalation summaries
        List<DashboardStats.EscalationSummary> escalationSummaries = activeEscalations.stream()
                .limit(10)
                .map(e -> DashboardStats.EscalationSummary.builder()
                        .ticketId(e.getId())
                        .guestName(e.getReservation().getGuest().getFullName())
                        .roomNumber(e.getReservation().getRoomNumber())
                        .reason(e.getReason())
                        .priority(e.getPriority().name())
                        .status(e.getStatus().name())
                        .createdAt(e.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .build())
                .toList();

        return DashboardStats.builder()
                .activeConversations(activeConversations)
                .totalConversationsToday(totalToday)
                .pendingEscalations(pendingEscalations)
                .housekeepingPending(housekeepingPending)
                .averageSentiment(avgSentiment != null ? avgSentiment : 0.0)
                .sentimentDistribution(sentimentDist)
                .recentConversations(recentConvos)
                .activeEscalations(escalationSummaries)
                .build();
    }
}
