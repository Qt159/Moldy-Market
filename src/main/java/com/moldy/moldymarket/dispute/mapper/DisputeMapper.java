package com.moldy.moldymarket.dispute.mapper;

import com.moldy.moldymarket.dispute.dto.response.DisputeResponse;
import com.moldy.moldymarket.dispute.dto.response.DisputeSummaryResponse;
import com.moldy.moldymarket.dispute.entity.DisputeRequest;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DisputeMapper {

    @Mapping(target = "media", ignore = true)
    DisputeResponse toDisputeResponse(DisputeRequest request);

    @Mapping(target = "id", source = "disputeRequest.id")
    @Mapping(target = "orderId", source = "disputeRequest.orderId")
    @Mapping(target = "orderCode", source = "order.orderCode")
    @Mapping(target = "firstItemTitle", expression = "java(extractFirstItemTitle(order))")
    @Mapping(target = "firstItemImageUrl", expression = "java(extractFirstItemImageUrl(order))")
    @Mapping(target = "totalAmount", source = "order.totalAmount")
    @Mapping(target = "reason", source = "disputeRequest.reason")
    @Mapping(target = "status", source = "disputeRequest.status")
    @Mapping(target = "decisionNote", source = "disputeRequest.decisionNote")
    @Mapping(target = "createdAt", source = "disputeRequest.createdAt")
    DisputeSummaryResponse toDisputeSummaryResponse(DisputeRequest disputeRequest, Order order);

    default String extractFirstItemTitle(Order order) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return null;
        }
        OrderItem first = order.getOrderItems().get(0);
        return first != null ? first.getProductName() : null;
    }

    default String extractFirstItemImageUrl(Order order) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return null;
        }
        OrderItem first = order.getOrderItems().get(0);
        return first != null ? first.getImageUrl() : null;
    }
}
