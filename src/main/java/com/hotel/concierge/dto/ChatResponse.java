package com.hotel.concierge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {

    private String sessionId;
    private String message;
    private String sentiment;
    private Double sentimentScore;
    private String guestPhone;
    private List<QuickAction> quickActions;
    private List<RecommendationCard> recommendations;
    private boolean escalated;
    private LocalDateTime timestamp;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuickAction {
        private String label;
        private String action;
        private String icon;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendationCard {
        private String title;
        private String description;
        private String imageUrl;
        private String category;
        private Double price;
        private String currency;
        private String actionUrl;
    }
}
