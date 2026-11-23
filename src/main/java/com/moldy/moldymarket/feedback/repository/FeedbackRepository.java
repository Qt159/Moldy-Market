package com.moldy.moldymarket.feedback.repository;

import com.moldy.moldymarket.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    boolean existsByOrderIdAndBuyerId(UUID orderId, UUID buyerId);

}
