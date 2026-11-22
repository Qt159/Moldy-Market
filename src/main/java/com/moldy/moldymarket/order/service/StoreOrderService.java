package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreOrderService {
    private final OrderRepository orderRepository;
    private final CurrentUser currentUser;
    private final StoreAccessGuard storeAccessGuard;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getStoreOrders(UUID storeId, int page, int size, OrderStatus status){
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        UUID userId = currentUser.findCurrentUser().getId();
        storeAccessGuard.requireStoreAccess(storeId, userId, PermissionConstants.ORDER_PROCESS);
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var orders = orderRepository.findStoreOrders(storeId, status, pageable);
        var content = orders.stream().map(orderMapper::toOrderResponse).toList();
        return PagedResponse.of(orders, content, pageable);

    }

}
