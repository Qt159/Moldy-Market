package com.moldy.moldymarket.order.jobs;

import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.service.CheckoutExpirationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutExpirationSchedulerTest {
    @Mock CheckoutGroupRepository repository;
    @Mock CheckoutExpirationService expirationService;
    @InjectMocks CheckoutExpirationScheduler scheduler;

    @Test
    void expiresEachDueCheckoutUsingTheSameCutoff() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(repository.findExpiredCheckoutIds(any(Instant.class))).thenReturn(List.of(first, second));
        scheduler.expireCheckouts();
        var cutoff = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repository).findExpiredCheckoutIds(cutoff.capture());
        verify(expirationService).expire(first, cutoff.getValue());
        verify(expirationService).expire(second, cutoff.getValue());
    }

    @Test
    void oneFailureDoesNotPreventOtherCheckoutsFromExpiring() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(repository.findExpiredCheckoutIds(any())).thenReturn(List.of(first, second));
        when(expirationService.expire(eq(first), any())).thenThrow(new IllegalStateException("Conflict"));
        scheduler.expireCheckouts();
        verify(expirationService).expire(eq(second), any());
    }

    @Test
    void leavesTheServiceAloneWhenNothingIsDue() {
        when(repository.findExpiredCheckoutIds(any())).thenReturn(List.of());
        scheduler.expireCheckouts();
        verifyNoInteractions(expirationService);
    }
}
