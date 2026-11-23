package com.moldy.moldymarket.feedback.mapper;

import com.moldy.moldymarket.feedback.dto.request.FeedbackCreationRequest;
import com.moldy.moldymarket.feedback.dto.response.FeedbackResponse;
import com.moldy.moldymarket.feedback.entity.Feedback;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FeedbackMapper {
    FeedbackResponse toFeedbackResponse(Feedback feedback);

    Feedback toFeedback(FeedbackCreationRequest request);
}
