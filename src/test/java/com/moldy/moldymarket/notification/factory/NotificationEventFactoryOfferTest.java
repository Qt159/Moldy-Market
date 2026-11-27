package com.moldy.moldymarket.notification.factory;

import com.moldy.moldymarket.offer.entity.Offer;
import com.moldy.moldymarket.offer.enums.OfferSide;
import com.moldy.moldymarket.offer.enums.OfferStatus;
import com.moldy.moldymarket.offer.event.OfferEvent;
import com.moldy.shared.notification.NotificationEvent;
import com.moldy.shared.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventFactoryOfferTest {

    private final NotificationEventFactory factory = new NotificationEventFactory();

    @Test
    void fromOfferEvent_mapsInitialPendingToOfferReceivedAndCounterToOfferCountered() {
        UUID recipientId = UUID.randomUUID();

        Offer initialOffer = buildOffer(null);
        List<NotificationEvent> initialEvents = factory.from(OfferEvent.builder()
                .status(OfferStatus.PENDING)
                .offer(initialOffer)
                .recipientId(recipientId)
                .build());

        assertThat(initialEvents).hasSize(1);
        assertThat(initialEvents.get(0).type()).isEqualTo(NotificationType.OFFER_RECEIVED);
        assertThat(initialEvents.get(0).userId()).isEqualTo(recipientId.toString());

        Offer counterOffer = buildOffer(UUID.randomUUID());
        List<NotificationEvent> counterEvents = factory.from(OfferEvent.builder()
                .status(OfferStatus.PENDING)
                .offer(counterOffer)
                .recipientId(recipientId)
                .build());

        assertThat(counterEvents).hasSize(1);
        assertThat(counterEvents.get(0).type()).isEqualTo(NotificationType.OFFER_COUNTERED);
    }

    @Test
    void fromOfferEvent_mapsAcceptedAndIgnoresSuperseded() {
        UUID recipientId = UUID.randomUUID();
        Offer offer = buildOffer(null);

        List<NotificationEvent> accepted = factory.from(OfferEvent.builder()
                .status(OfferStatus.ACCEPTED)
                .offer(offer)
                .recipientId(recipientId)
                .build());

        assertThat(accepted).hasSize(1);
        assertThat(accepted.get(0).type()).isEqualTo(NotificationType.OFFER_ACCEPTED);

        List<NotificationEvent> superseded = factory.from(OfferEvent.builder()
                .status(OfferStatus.SUPERSEDED)
                .offer(offer)
                .recipientId(recipientId)
                .build());

        assertThat(superseded).isEmpty();
    }

    private Offer buildOffer(UUID previousOfferId) {
        Offer offer = Offer.builder()
                .productId(UUID.randomUUID())
                .buyerId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .originalPrice(new BigDecimal("1000000"))
                .price(new BigDecimal("850000"))
                .side(OfferSide.BUYER)
                .negotiationId(UUID.randomUUID())
                .previousOfferId(previousOfferId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        ReflectionTestUtils.setField(offer, "id", UUID.randomUUID());
        return offer;
    }
}
