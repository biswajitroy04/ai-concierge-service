package com.hotel.concierge.service.sentiment;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SentimentAnalysisService {

    private final ChatLanguageModel chatLanguageModel;

    private static final List<String> ESCALATION_KEYWORDS = List.of(
            "manager", "supervisor", "complaint", "unacceptable", "terrible",
            "worst", "disgusting", "lawsuit", "refund", "furious", "outraged",
            "speak to someone", "human", "real person", "not acceptable"
    );

    public SentimentResult analyzeSentiment(String text) {
        try {
            String prompt = String.format("""
                    Analyze the sentiment of this hotel guest message. 
                    Return ONLY a JSON object with two fields:
                    - "score": a number between -1.0 (very negative) and 1.0 (very positive)
                    - "label": one of "VERY_POSITIVE", "POSITIVE", "NEUTRAL", "NEGATIVE", "VERY_NEGATIVE"
                    
                    Message: "%s"
                    
                    JSON:
                    """, text);

            String response = chatLanguageModel.generate(prompt);
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("Sentiment analysis failed, defaulting to neutral: {}", e.getMessage());
            return new SentimentResult(0.0, "NEUTRAL");
        }
    }

    public boolean requiresEscalation(String message) {
        String lowerMessage = message.toLowerCase();
        return ESCALATION_KEYWORDS.stream().anyMatch(lowerMessage::contains);
    }

    private SentimentResult parseResponse(String response) {
        try {
            double score = 0.0;
            String label = "NEUTRAL";

            // Normalize to single line for regex matching
            String flat = response.replaceAll("\\s+", " ");

            // Extract score using a more robust pattern
            java.util.regex.Matcher scoreMatcher = java.util.regex.Pattern
                    .compile("\"score\"\\s*:\\s*(-?\\d+\\.?\\d*)")
                    .matcher(flat);
            if (scoreMatcher.find()) {
                score = Double.parseDouble(scoreMatcher.group(1));
            }

            if (flat.contains("VERY_POSITIVE")) label = "VERY_POSITIVE";
            else if (flat.contains("VERY_NEGATIVE")) label = "VERY_NEGATIVE";
            else if (flat.contains("POSITIVE")) label = "POSITIVE";
            else if (flat.contains("NEGATIVE")) label = "NEGATIVE";
            else label = "NEUTRAL";

            log.debug("Sentiment parsed: score={}, label={}, raw={}", score, label, flat);
            return new SentimentResult(score, label);
        } catch (Exception e) {
            log.warn("Sentiment parsing failed: {}", e.getMessage());
            return new SentimentResult(0.0, "NEUTRAL");
        }
    }

    public record SentimentResult(double score, String label) {
        public double getScore() { return score; }
        public String getLabel() { return label; }
    }
}
