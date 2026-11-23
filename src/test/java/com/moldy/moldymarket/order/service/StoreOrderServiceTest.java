package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderResponseAssembler;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import com.moldy.moldymarket.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreOrderServiceTest {
    @Mock OrderRepository repository;
    @Mock CurrentUser currentUser;
    @Mock StoreAccessGuard guard;
    @Mock OrderResponseAssembler mapper;
    @InjectMocks StoreOrderService service;
    UUID userId = UUID.randomUUID(), storeId = UUID.randomUUID();

    @Test void checksStorePermissionBeforeQueryAndMapsUsingStableNewestFirstPaging() {
        authenticate();
        var order = mock(Order.class);
        var response = mock(OrderResponse.class);
        var request = PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(repository.findStoreOrders(eq(storeId), eq(OrderStatus.PENDING_CONFIRMED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), request, 3));
        when(mapper.toResponses(List.of(order))).thenReturn(List.of(response));
        var result = service.getStoreOrders(storeId, 2, 1, OrderStatus.PENDING_CONFIRMED);
        assertThat(result.content()).containsExactly(response);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
        InOrder calls = inOrder(currentUser, guard, repository);
        calls.verify(currentUser).findCurrentUser();
        calls.verify(guard).requireStoreAccess(storeId, userId, PermissionConstants.ORDER_PROCESS);
        calls.verify(repository).findStoreOrders(storeId, OrderStatus.PENDING_CONFIRMED, request);
    }

    @Test void emptyPageKeepsMetadataAndNeverThrowsTheLegacyBuyer404() {
        authenticate();
        when(repository.findStoreOrders(eq(storeId), isNull(), any(Pageable.class)))
                .thenAnswer(call -> Page.empty(call.getArgument(2)));
        var result = service.getStoreOrders(storeId, 1, 20, null);
        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isZero();
        assertThat(result.hasNext()).isFalse();
        verify(mapper).toResponses(List.of());
    }

    @Test void deniedMembershipNeverQueriesOrMapsCustomerData() {
        authenticate();
        when(guard.requireStoreAccess(storeId, userId, PermissionConstants.ORDER_PROCESS))
                .thenThrow(new AppException(ErrorCode.STORE_ACCESS_DENIED));
        assertThatThrownBy(() -> service.getStoreOrders(storeId, 1, 20, null))
                .isInstanceOf(AppException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_ACCESS_DENIED);
        verifyNoInteractions(repository, mapper);
    }

    @ParameterizedTest
    @CsvSource({"0,20", "-1,20", "1,0", "1,101", "2147483647,100"})
    void rejectsBadPageBoundsAndJpaOffsetOverflow(int page, int size) {
        assertThatThrownBy(() -> service.getStoreOrders(storeId, page, size, null))
                .isInstanceOf(AppException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(currentUser, guard, repository, mapper);
    }

    private void authenticate() {
        var user = new User(); user.setId(userId);
        when(currentUser.findCurrentUser()).thenReturn(user);
    }
}
