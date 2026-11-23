package com.moldy.moldymarket.feedback.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.feedback.dto.request.FeedbackCreationRequest;
import com.moldy.moldymarket.feedback.dto.request.FeedbackUpdateRequest;
import com.moldy.moldymarket.feedback.dto.response.FeedbackResponse;
import com.moldy.moldymarket.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FeedbackController {
    FeedbackService feedbackService;

    @PostMapping
    ResponseEntity<ApiResponse<FeedbackResponse>> createFeedback(@Valid @RequestBody FeedbackCreationRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(feedbackService.createFeedback(request)));
    }

    @PatchMapping("/{feedbackId}")
    ResponseEntity<ApiResponse<FeedbackResponse>> updateFeedback(@PathVariable UUID feedbackId, @Valid @RequestBody FeedbackUpdateRequest request){
        return ResponseEntity.ok(ApiResponse.success(feedbackService.updateFeedback(feedbackId, request)));
    }


    @DeleteMapping("/{feedbackId}")
    public void deleteFeedback(@PathVariable UUID feedbackId) {
        feedbackService.deleteFeedback(feedbackId);
    }

}
