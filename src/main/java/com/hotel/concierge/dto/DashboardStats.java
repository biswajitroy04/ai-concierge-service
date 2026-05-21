package com.hotel.concierge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStats {

    private long activeConversations;
    private long totalConversationsToday;
    private long pendingEscalations;
    private long housekeepingPending;
    private double averageSentiment;
    private long upsellConversions;
    private double upsellRevenue;
    private Map<String, Long> sentimentDistribution;
    private List<ConversationSummary> recentConversations;
    private List<EscalationSummary> activeEscalations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConversationSummary {
        private String sessionId;
        private String guestName;
        private String roomNumber;
        private String lastMessage;
        private String sentiment;
        private String status;
        private String startedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EscalationSummary {
        private Long ticketId;
        private String guestName;
        private String roomNumber;
        private String reason;
        private String priority;
        private String status;
        private String createdAt;
    }
}
