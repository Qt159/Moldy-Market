package com.moldy.moldymarket.offer.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.offer.dto.request.OfferCounterRequest;
import com.moldy.moldymarket.offer.dto.request.OfferCreationRequest;
import com.moldy.moldymarket.offer.dto.response.OfferResponse;
import com.moldy.moldymarket.offer.dto.response.PageResponse;
import com.moldy.moldymarket.offer.entity.Offer;
import com.moldy.moldymarket.offer.enums.OfferSide;
import com.moldy.moldymarket.offer.enums.OfferStatus;
import com.moldy.moldymarket.offer.event.OfferEvent;
import com.moldy.moldymarket.offer.mapper.OfferMapper;
import com.moldy.moldymarket.offer.repository.OfferRepository;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OfferMapper offerMapper;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private User buyer;

    @Mock
    private User seller;

    @Mock
    private Product product;

    @InjectMocks
    private OfferService offerService;

    private UUID buyerId;
    private UUID sellerId;
    private UUID storeId;
    private UUID productId;

    private final BigDecimal originalPrice =
            new BigDecimal("1000000");

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        productId = UUID.randomUUID();

        ReflectionTestUtils.setField(
                offerService,
                "minPricePercent",
                new BigDecimal("0.75")
        );

        ReflectionTestUtils.setField(
                offerService,
                "maxPricePercent",
                new BigDecimal("1.00")
        );

        ReflectionTestUtils.setField(
                offerService,
                "maxBuyerOffersPerProduct",
                3
        );

        ReflectionTestUtils.setField(
                offerService,
                "responseHours",
                24L
        );

        ReflectionTestUtils.setField(
                offerService,
                "acceptedCheckoutHours",
                1L
        );

        ReflectionTestUtils.setField(
                offerService,
                "maxPageSize",
                50
        );
    }
    // =========================================================
    // CREATE OFFER
    // =========================================================

    @Test
    void createOffer_shouldCreateOffer_whenRequestIsValid() {

        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(buyer.getId()).thenReturn(buyerId);

        prepareActiveProduct();

        OfferCreationRequest request =
                new OfferCreationRequest(
                        productId,
                        new BigDecimal("750000")
                );

        Offer savedOffer = mock(Offer.class);

        when(offerRepository.save(any(Offer.class)))
                .thenReturn(savedOffer);

        OfferResponse response = mock(OfferResponse.class);

        when(offerMapper.toOfferResponse(savedOffer))
                .thenReturn(response);

        OfferResponse result =
                offerService.createOffer(request);

        assertSame(response, result);

        ArgumentCaptor<Offer> captor =
                ArgumentCaptor.forClass(Offer.class);

        verify(offerRepository).save(captor.capture());

        Offer createdOffer = captor.getValue();

        assertEquals(productId, createdOffer.getProductId());
        assertEquals(buyerId, createdOffer.getBuyerId());
        assertEquals(sellerId, createdOffer.getSellerId());
        assertEquals(storeId, createdOffer.getStoreId());

        assertEquals(
                originalPrice,
                createdOffer.getOriginalPrice()
        );

        assertEquals(
                new BigDecimal("750000"),
                createdOffer.getPrice()
        );

        assertEquals(
                OfferSide.BUYER,
                createdOffer.getSide()
        );

        assertEquals(
                OfferStatus.PENDING,
                createdOffer.getStatus()
        );

        assertNull(createdOffer.getPreviousOfferId());
        assertNotNull(createdOffer.getNegotiationId());
        assertNotNull(createdOffer.getExpiresAt());

        verify(applicationEventPublisher)
                .publishEvent(any(OfferEvent.class));

        verify(offerMapper)
                .toOfferResponse(savedOffer);
    }

    @Test
    void createOffer_shouldReject_whenPriceIsBelowMinimumPercentage() {

        when(currentUser.findCurrentUser())
                .thenReturn(buyer);
        when(buyer.getId()).thenReturn(buyerId);

        prepareProductFound();
        when(product.getPrice()).thenReturn(originalPrice);

        OfferCreationRequest request =
                new OfferCreationRequest(
                        productId,
                        new BigDecimal("749999")
                );

        assertThrows(
                AppException.class,
                () -> offerService.createOffer(request)
        );

        verify(offerRepository, never())
                .save(any());
    }

    @Test
    void createOffer_shouldAccept_whenPriceEqualsMinimumPercentage() {

        when(currentUser.findCurrentUser())
                .thenReturn(buyer);
        when(buyer.getId()).thenReturn(buyerId);

        prepareActiveProduct();

        OfferCreationRequest request =
                new OfferCreationRequest(
                        productId,
                        new BigDecimal("750000")
                );

        Offer savedOffer = mock(Offer.class);

        when(offerRepository.save(any(Offer.class)))
                .thenReturn(savedOffer);

        when(offerMapper.toOfferResponse(savedOffer))
                .thenReturn(mock(OfferResponse.class));

        assertDoesNotThrow(
                () -> offerService.createOffer(request)
        );

        verify(offerRepository).save(any(Offer.class));
    }
    @Test
    void createOffer_shouldReject_whenPriceEqualsOriginalPrice() {

        when(currentUser.findCurrentUser())
                .thenReturn(buyer);
        when(buyer.getId()).thenReturn(buyerId);

        prepareProductForPriceValidation();

        OfferCreationRequest request =
                new OfferCreationRequest(
                        productId,
                        originalPrice
                );

        assertThrows(
                AppException.class,
                () -> offerService.createOffer(request)
        );

        verify(offerRepository, never())
                .save(any());
    }
    @Test
    void createOffer_shouldReject_whenBuyerOfferLimitReached() {

        when(currentUser.findCurrentUser())
                .thenReturn(buyer);
        when(buyer.getId()).thenReturn(buyerId);

        prepareProductForOfferLimitCheck();

        when(offerRepository
                .countByBuyerIdAndProductIdAndSide(
                        buyerId,
                        productId,
                        OfferSide.BUYER
                ))
                .thenReturn(3L);

        OfferCreationRequest request =
                new OfferCreationRequest(
                        productId,
                        new BigDecimal("750000")
                );

        assertThrows(
                AppException.class,
                () -> offerService.createOffer(request)
        );

        verify(offerRepository, never())
                .save(any());
    }

    @Test
    void accept_shouldAcceptOffer_whenSellerAcceptsBuyerOffer() {

        when(currentUser.findCurrentUser())
                .thenReturn(seller);
        when(seller.getId()).thenReturn(sellerId);

        Offer offer = mockOffer();
        when(offer.getId()).thenReturn(UUID.randomUUID());
        when(offer.getBuyerId()).thenReturn(buyerId);
        when(offer.getSellerId()).thenReturn(sellerId);
        when(offer.getProductId()).thenReturn(productId);

        when(offerRepository.findByIdForUpdate(offer.getId()))
                .thenReturn(Optional.of(offer));

        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        when(product.getStatus())
                .thenReturn(ProductStatus.ACTIVE);

        when(product.isAllowOffer())
                .thenReturn(true);

        when(offerRepository.save(offer))
                .thenReturn(offer);

        OfferResponse response =
                mock(OfferResponse.class);

        when(offerMapper.toOfferResponse(offer))
                .thenReturn(response);

        OfferResponse result =
                offerService.accept(offer.getId());

        assertSame(response, result);

        verify(offer).accept(
                eq(OfferSide.SELLER),
                any(Instant.class),
                any(Instant.class)
        );

        verify(offerRepository)
                .save(offer);

        verify(applicationEventPublisher)
                .publishEvent(any(OfferEvent.class));

        verify(offerMapper)
                .toOfferResponse(offer);
    }

    @Test
    void getSentOffers_shouldReturnNonSupersededOffers() {

        when(currentUser.findCurrentUser())
                .thenReturn(buyer);
        when(buyer.getId()).thenReturn(buyerId);

        Offer offer = mockOffer();

        OfferResponse response =
                mock(OfferResponse.class);

        when(offerMapper.toOfferResponse(offer))
                .thenReturn(response);

        PageImpl<Offer> page =
                new PageImpl<>(
                        List.of(offer),
                        PageRequest.of(0, 10),
                        1
                );

        when(offerRepository.findAllByBuyerIdAndStatusNot(
                eq(buyerId),
                eq(OfferStatus.SUPERSEDED),
                any()
        )).thenReturn(page);

        PageResponse<OfferResponse> result =
                offerService.getSentOffers(1, 10);

        assertEquals(1, result.currentPage());
        assertEquals(10, result.pageSize());
        assertEquals(1, result.totalElements());

        assertEquals(
                List.of(response),
                result.data()
        );

        verify(offerMapper)
                .toOfferResponse(offer);

        verify(offerRepository, never())
                .findTopByNegotiationIdOrderByCreatedAtDescIdDesc(any());
    }
    @Test
    void getReceivedOffers_shouldReturnNonSupersededOffers() {

        when(currentUser.findCurrentUser())
                .thenReturn(seller);
        when(seller.getId()).thenReturn(sellerId);

        Offer offer = mockOffer();

        OfferResponse response =
                mock(OfferResponse.class);

        when(offerMapper.toOfferResponse(offer))
                .thenReturn(response);

        PageImpl<Offer> page =
                new PageImpl<>(
                        List.of(offer),
                        PageRequest.of(0, 10),
                        1
                );

        when(offerRepository.findAllBySellerIdAndStatusNot(
                eq(sellerId),
                eq(OfferStatus.SUPERSEDED),
                any()
        )).thenReturn(page);

        PageResponse<OfferResponse> result =
                offerService.getReceivedOffers(1, 10);

        assertEquals(1, result.currentPage());
        assertEquals(10, result.pageSize());
        assertEquals(1, result.totalElements());

        assertEquals(
                List.of(response),
                result.data()
        );

        verify(offerMapper)
                .toOfferResponse(offer);

        verify(offerRepository, never())
                .findTopByNegotiationIdOrderByCreatedAtDescIdDesc(any());
    }
    // =========================================================
    // HELPERS
    // =========================================================

    private void prepareActiveProduct() {
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        when(product.getId()).thenReturn(productId);
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);
        when(product.isAllowOffer()).thenReturn(true);
        when(product.getSellerId()).thenReturn(sellerId);
        when(product.getStoreId()).thenReturn(storeId);
        when(product.getPrice()).thenReturn(originalPrice);
    }
    private void prepareProductFound() {
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        when(product.getStatus())
                .thenReturn(ProductStatus.ACTIVE);

        when(product.isAllowOffer())
                .thenReturn(true);

        when(product.getSellerId())
                .thenReturn(sellerId);
    }
    private void prepareProductForPriceValidation() {
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        when(product.getStatus())
                .thenReturn(ProductStatus.ACTIVE);

        when(product.isAllowOffer())
                .thenReturn(true);

        when(product.getSellerId())
                .thenReturn(sellerId);

        when(product.getPrice())
                .thenReturn(originalPrice);
    }
    private void prepareProductForOfferLimitCheck() {
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        when(product.getId()).thenReturn(productId);
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);
        when(product.isAllowOffer()).thenReturn(true);
        when(product.getSellerId()).thenReturn(sellerId);
    }
    private Offer mockOffer() {
        return mock(Offer.class);
    }
}