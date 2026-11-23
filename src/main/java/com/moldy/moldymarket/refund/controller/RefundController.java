package com.moldy.moldymarket.refund.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.refund.dto.response.RefundResponse;
import com.moldy.moldymarket.refund.service.RefundService;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefundController {
    RefundService refundService;

    //admin
    @IsAdmin
    @PostMapping("/admin/{id}")
    ResponseEntity<ApiResponse<RefundResponse>> confirmRefund(@PathVariable UUID id){
        return ResponseEntity.ok(ApiResponse.success(refundService.confirmRefund(id)));
    }

    @IsAdmin
    @GetMapping("/admin")
    ResponseEntity<ApiResponse<List<RefundResponse>>> getRefundsForAdmin(){
        return ResponseEntity.ok(ApiResponse.success(refundService.getAllRefundsForAdmin()));
    }
}
