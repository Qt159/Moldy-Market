package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.dto.response.StoreOrderSummaryResponse;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderResponseAssembler;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreOrderService {
    private final OrderRepository orderRepository;
    private final CurrentUser currentUser;
    private final StoreAccessGuard storeAccessGuard;
    private final OrderResponseAssembler orderResponses;

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getStoreOrders(UUID storeId, int page, int size, OrderStatus status) {
        return getStoreOrders(storeId, page, size, status, null, null, null, false);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getStoreOrders(
            UUID storeId,
            int page,
            int size,
            OrderStatus status,
            String keyword,
            LocalDate fromDate,
            LocalDate toDate,
            boolean oldestFirst
    ) {
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        UUID userId = currentUser.findCurrentUser().getId();
        storeAccessGuard.requireStoreAccess(storeId, userId, PermissionConstants.ORDER_PROCESS);

        Sort.Direction direction = oldestFirst ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(page - 1, size, Sort.by(direction, "createdAt", "id"));

        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        boolean hasAdvancedFilter = !trimmedKeyword.isEmpty() || fromDate != null || toDate != null;

        if (!hasAdvancedFilter) {
            var orders = orderRepository.findStoreOrders(storeId, status, pageable);
            var content = orderResponses.toResponses(orders.getContent());
            return PagedResponse.of(orders, content, pageable);
        }

        String keywordPattern = trimmedKeyword.isEmpty()
                ? null
                : "%" + trimmedKeyword.toLowerCase(Locale.ROOT) + "%";
        Instant fromInstant = fromDate == null ? null : fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = toDate == null ? null : toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        var idPage = orderRepository.findStoreOrderIdsFiltered(
                storeId,
                status,
                keywordPattern,
                fromInstant,
                toInstant,
                pageable
        );

        List<UUID> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return PagedResponse.of(idPage, orderResponses.toResponses(List.of()), pageable);
        }

        Map<UUID, Order> ordersById = orderRepository.findAllByIdInWithItemsAndCheckoutGroup(ids)
                .stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<Order> ordered = ids.stream()
                .map(ordersById::get)
                .filter(order -> order != null)
                .toList();

        var content = orderResponses.toResponses(ordered);
        return PagedResponse.of(idPage, content, pageable);
    }

    @Transactional(readOnly = true)
    public StoreOrderSummaryResponse getStoreOrderSummary(UUID storeId) {
        UUID userId = currentUser.findCurrentUser().getId();
        storeAccessGuard.requireStoreAccess(storeId, userId, PermissionConstants.ORDER_PROCESS);

        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        long total = 0L;
        for (Object[] row : orderRepository.countStoreOrdersByStatus(storeId)) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            OrderStatus orderStatus = (OrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            counts.put(orderStatus, count);
            total += count;
        }
        return new StoreOrderSummaryResponse(total, counts);
    }
}
