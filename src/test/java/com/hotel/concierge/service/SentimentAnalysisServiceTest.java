package com.hotel.concierge.service;

import com.hotel.concierge.service.sentiment.SentimentAnalysisService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SentimentAnalysisServiceTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @InjectMocks
    private SentimentAnalysisService sentimentService;

    @Test
    void analyzeSentiment_positiveMessage() {
        when(chatLanguageModel.generate(anyString()))
                .thenReturn("{\"score\": 0.8, \"label\": \"VERY_POSITIVE\"}");

        var result = sentimentService.analyzeSentiment("This hotel is amazing! Best stay ever!");

        assertThat(result.getScore()).isEqualTo(0.8);
        assertThat(result.getLabel()).isEqualTo("VERY_POSITIVE");
    }

    @Test
    void analyzeSentiment_negativeMessage() {
        when(chatLanguageModel.generate(anyString()))
                .thenReturn("{\"score\": -0.7, \"label\": \"NEGATIVE\"}");

        var result = sentimentService.analyzeSentiment("The room is dirty and the AC doesn't work");

        assertThat(result.getScore()).isEqualTo(-0.7);
        assertThat(result.getLabel()).isEqualTo("NEGATIVE");
    }

    @Test
    void analyzeSentiment_handlesError() {
        when(chatLanguageModel.generate(anyString())).thenThrow(new RuntimeException("API error"));

        var result = sentimentService.analyzeSentiment("test message");

        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getLabel()).isEqualTo("NEUTRAL");
    }

    @Test
    void requiresEscalation_detectsKeywords() {
        assertThat(sentimentService.requiresEscalation("I want to speak to the manager")).isTrue();
        assertThat(sentimentService.requiresEscalation("This is unacceptable")).isTrue();
        assertThat(sentimentService.requiresEscalation("I need a real person")).isTrue();
        assertThat(sentimentService.requiresEscalation("Can I get extra towels?")).isFalse();
    }
}
