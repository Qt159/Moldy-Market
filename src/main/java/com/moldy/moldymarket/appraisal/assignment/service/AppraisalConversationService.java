package com.moldy.moldymarket.appraisal.assignment.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for managing conversations related to appraisal assignments.
 * This is a bridge between the appraisal module and the chat module.
 * TODO: Integrate with module Chat when unified API for creating conversation is finalized.
 */
@Service
public class AppraisalConversationService {

    /**
     * Creates a private conversation between a seller and an appraiser.
     *
     * @param sellerId the ID of the seller
     * @param appraiserId the ID of the appraiser
     * @return the ID of the created conversation
     */
    public UUID createConversation(UUID sellerId, UUID appraiserId) {
        // Implementation will be added when chat module API is finalized
        // For now, generate a placeholder conversation ID
        return UUID.randomUUID();
    }
}
