package com.hotel.concierge.service.ai;

import dev.langchain4j.service.SystemMessage;

/**
 * LangChain4J AI Service interface for the hotel concierge.
 * This interface is implemented dynamically by LangChain4J's AiServices.
 */
public interface ConciergeAssistant {

    String chat(String userMessage);
}
