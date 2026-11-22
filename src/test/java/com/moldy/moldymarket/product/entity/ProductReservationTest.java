package com.moldy.moldymarket.product.entity;

import com.moldy.moldymarket.common.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductReservationTest {
    private final Instant now = Instant.parse("2026-11-19T03:00:00Z");
    private final UUID checkoutId = UUID.randomUUID();
    private final UUID otherCheckoutId = UUID.randomUUID();

    @Test
    void onlineReservationEndsExactlyAtItsDeadline() {
        Product product = product();
        Instant deadline = now.plusSeconds(900);
        product.reserveForCheckout(checkoutId, deadline, now);

        assertThat(product.isReservedAt(deadline.minusNanos(1))).isTrue();
        assertThat(product.isReservedAt(deadline)).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void anotherCheckoutCannotTakeAnUnexpiredReservation() {
        Product product = product();
        product.reserveForCheckout(checkoutId, now.plusSeconds(900), now);

        assertThatThrownBy(() -> product.reserveForCheckout(otherCheckoutId, null, now))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode().name()).isEqualTo("PRODUCT_RESERVED"));
        assertThat(product.getReservedCheckoutId()).isEqualTo(checkoutId);
    }

    @Test
    void codReservationHasNoAutomaticDeadline() {
        Product product = product();
        product.reserveForCheckout(checkoutId, null, now);

        assertThat(product.isReservedAt(now.plusSeconds(30L * 24 * 3600))).isTrue();
        assertThat(product.getReservationExpiresAt()).isNull();
    }

    @Test
    void retryingTheSameReservationDoesNotExtendItsDeadline() {
        Product product = product();
        Instant deadline = now.plusSeconds(900);
        product.reserveForCheckout(checkoutId, deadline, now);
        product.reserveForCheckout(checkoutId, deadline.plusSeconds(900), now.plusSeconds(1));

        assertThat(product.getReservationExpiresAt()).isEqualTo(deadline);
    }

    @Test
    void anOldCheckoutCannotReleaseANewerReservation() {
        Product product = product();
        Instant deadline = now.plusSeconds(900);
        product.reserveForCheckout(checkoutId, deadline, now);
        product.reserveForCheckout(otherCheckoutId, null, deadline);

        assertThat(product.releaseReservationFor(checkoutId, deadline)).isFalse();
        assertThat(product.getReservedCheckoutId()).isEqualTo(otherCheckoutId);
        assertThat(product.isReservedAt(deadline)).isTrue();
    }

    @Test
    void releasingTheOwningCheckoutClearsOnlyItsReservation() {
        Product product = product();
        product.reserveForCheckout(checkoutId, null, now);

        assertThat(product.releaseReservationFor(checkoutId, now)).isTrue();
        assertThat(product.releaseReservationFor(checkoutId, now)).isFalse();
        assertThat(product.getReservedCheckoutId()).isNull();
        assertThat(product.getReservationExpiresAt()).isNull();
        assertThat(product.isReservedAt(now)).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void soldProductsCannotBeReserved() {
        Product product = product();
        product.changeStatus(ProductStatus.SOLD);

        assertThatThrownBy(() -> product.reserveForCheckout(checkoutId, null, now))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode().name()).isEqualTo("PRODUCT_NOT_ACTIVE"));
        assertThat(product.getReservedCheckoutId()).isNull();
    }

    @Test
    void aNewTimedReservationMustHaveAFutureDeadline() {
        Product product = product();

        assertThatThrownBy(() -> product.reserveForCheckout(checkoutId, now, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(product.getReservedCheckoutId()).isNull();
    }

    private Product product() {
        return new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Used book", "Description", BigDecimal.valueOf(100000),
                ProductCondition.USED, PricingStrategy.SELLER_SET, List.of(), Map.of());
    }
}
