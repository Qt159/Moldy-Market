package com.moldy.moldymarket.offer.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.storage.dto.response.MediaViewResponse;
import com.moldy.moldymarket.common.storage.service.FileStorageService;
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
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OfferService {
    OfferRepository offerRepository;
    CurrentUser currentUser;
    ProductRepository productRepository;
    OfferMapper offerMapper;
    ApplicationEventPublisher applicationEventPublisher;
    StoreAccessGuard storeAccessGuard;
    UserRepository userRepository;
    FileStorageService fileStorageService;

    @NonFinal
    @Value("${offer.min-price-percent}")
    BigDecimal minPricePercent;

    @NonFinal
    @Value("${offer.max-price-percent}")
    BigDecimal maxPricePercent;

    @NonFinal
    @Value("${offer.max-buyer-offers-per-product}")
    int maxBuyerOffersPerProduct;

    @NonFinal
    @Value("${offer.response-hours}")
    long responseHours;

    @NonFinal
    @Value("${offer.accepted-checkout-hours}")
    long acceptedCheckoutHours;
    @NonFinal
    @Value("${offer.max-page-size}")
    int maxPageSize;


    @Transactional
    public OfferResponse createOffer(OfferCreationRequest request){
        var user = currentUser.findCurrentUser();
        BigDecimal price = request.price();

        validateOfferPrice(price);
        var product = productRepository.findById(request.productId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        if(!product.getStatus().equals(ProductStatus.ACTIVE)) throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE);
        if(!product.isAllowOffer()) throw new AppException(ErrorCode.OFFER_NOT_ALLOWED);
        if (user.getId().equals(product.getSellerId())
                || (product.getStoreId() != null && storeAccessGuard.isStoreMember(product.getStoreId(), user.getId()))) {
            throw new AppException(ErrorCode.CANNOT_MAKE_OFFER_FIRST);
        }


        long initialOfferCount = offerRepository.countByBuyerIdAndProductIdAndSide(
                user.getId(), product.getId(), OfferSide.BUYER);

        if(initialOfferCount >= maxBuyerOffersPerProduct){
            throw new AppException(ErrorCode.OFFER_LIMIT_REACHED);
        }

        validateInitialOfferPrice(price, product.getPrice());

        if(offerRepository.existsByBuyerIdAndProductIdAndStatusIn(
                user.getId(),
                product.getId(),
                List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED)))
            throw new AppException(ErrorCode.OFFER_ALREADY_EXISTED);

        Offer offer = Offer.builder()
                .productId(product.getId())
                .buyerId(user.getId())
                .sellerId(product.getSellerId())
                .storeId(product.getStoreId())
                .originalPrice(product.getPrice())
                .price(price)
                .side(OfferSide.BUYER)
                .negotiationId(UUID.randomUUID())
                .previousOfferId(null)
                .expiresAt(Instant.now().plus(responseHours, ChronoUnit.HOURS))
                .note(request.note())
                .roundNumber((int) initialOfferCount + 1)
                .build();
        var savedOffer = offerRepository.save(offer);
        applicationEventPublisher.publishEvent(OfferEvent.builder()
                        .status(OfferStatus.PENDING)
                        .offer(savedOffer)
                        .recipientId(product.getSellerId())
                        .build());
        return enrichSingle(offerMapper.toOfferResponse(savedOffer));
    }

    @Transactional
    public OfferResponse accept(UUID id){
        var user = currentUser.findCurrentUser();
        var offer = getOfferForUpdate(id);
        OfferSide actor = getActorSide(offer, user.getId());
        validateProductAvailable(offer.getProductId());
        Instant now = Instant.now();
        offer.accept(actor, now, now.plus(acceptedCheckoutHours, ChronoUnit.HOURS));

        var savedOffer = offerRepository.save(offer);
        UUID recipientId = actor == OfferSide.BUYER ? offer.getSellerId() : offer.getBuyerId();
        applicationEventPublisher.publishEvent(OfferEvent.builder()
                        .status(OfferStatus.ACCEPTED)
                        .offer(savedOffer)
                        .recipientId(recipientId)
                        .build());
        return enrichSingle(offerMapper.toOfferResponse(savedOffer));
    }
    @Transactional
    public OfferResponse reject(UUID id){
        var user = currentUser.findCurrentUser();
        var offer = getOfferForUpdate(id);
        OfferSide actor = getActorSide(offer, user.getId());
        offer.reject(actor, Instant.now());
        var savedOffer = offerRepository.save(offer);
        UUID recipientId = actor == OfferSide.BUYER ? offer.getSellerId() : offer.getBuyerId();
        applicationEventPublisher.publishEvent(OfferEvent.builder()
                        .status(OfferStatus.REJECTED)
                        .offer(savedOffer)
                        .recipientId(recipientId)
                        .build());
        return enrichSingle(offerMapper.toOfferResponse(savedOffer));
    }
    //only buyer
    @Transactional
    public OfferResponse cancel(UUID id){
        var user = currentUser.findCurrentUser();
        var offer = getOfferForUpdate(id);
        if(!offer.getBuyerId().equals(user.getId())){
            throw new AppException(ErrorCode.OFFER_NOT_EXISTED);
        }
        offer.cancel(Instant.now());
        var savedOffer = offerRepository.save(offer);
        applicationEventPublisher.publishEvent(
                OfferEvent.builder()
                        .status(OfferStatus.CANCELLED)
                        .offer(savedOffer)
                        .recipientId(savedOffer.getSellerId())
                        .build()
        );
        return enrichSingle(offerMapper.toOfferResponse(savedOffer));
    }

    @Transactional
    public OfferResponse counter(UUID id, OfferCounterRequest request){
        var user = currentUser.findCurrentUser();

        var previousOffer = getOfferForUpdate(id);
        OfferSide actor = getActorSide(previousOffer, user.getId());
        validateProductAvailable(previousOffer.getProductId());
        int nextRound = Math.max(1, previousOffer.getRoundNumber());
        if(actor.equals(OfferSide.BUYER)){
            long buyerOfferCount = offerRepository.countByBuyerIdAndProductIdAndSide(
                    previousOffer.getBuyerId(),
                    previousOffer.getProductId(),
                    OfferSide.BUYER
            );
            if(buyerOfferCount >= maxBuyerOffersPerProduct){
                throw new AppException(ErrorCode.OFFER_LIMIT_REACHED);
            }
            nextRound = (int) buyerOfferCount + 1;
        }
        BigDecimal price = request.price();

        validateCounterPrice(
                previousOffer,
                actor,
                price
        );
        Instant now = Instant.now();
        //mark the previous offer as replaced
        previousOffer.supersede(actor, now);
        offerRepository.saveAndFlush(previousOffer);

        Offer newOffer = Offer.builder()
                .productId(previousOffer.getProductId())
                .buyerId(previousOffer.getBuyerId())
                .sellerId(previousOffer.getSellerId())
                .storeId(previousOffer.getStoreId())
                .originalPrice(previousOffer.getOriginalPrice())
                .price(price)
                .side(actor)
                .negotiationId(previousOffer.getNegotiationId())
                .previousOfferId(previousOffer.getId())
                .expiresAt(now.plus(responseHours,
                                ChronoUnit.HOURS))
                .note(request.note())
                .roundNumber(nextRound)
                .build();
        var savedOffer = offerRepository.save(newOffer);

        UUID recipientId = actor == OfferSide.BUYER ? savedOffer.getSellerId() : savedOffer.getBuyerId();
        applicationEventPublisher.publishEvent(
                OfferEvent.builder()
                        .status(OfferStatus.PENDING)
                        .offer(savedOffer)
                        .recipientId(recipientId)
                        .build()
        );

        return enrichSingle(offerMapper.toOfferResponse(savedOffer));
    }


    @Transactional(readOnly = true)
    public OfferResponse getActiveOfferForProduct(UUID productId) {
        var user = currentUser.findCurrentUser();
        Instant now = Instant.now();
        return offerRepository
                .findFirstByBuyerIdAndProductIdAndStatusInOrderByCreatedAtDescIdDesc(
                        user.getId(),
                        productId,
                        List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED)
                )
                .filter(offer -> {
                    if (offer.getStatus() == OfferStatus.PENDING) {
                        return offer.getExpiresAt() != null && now.isBefore(offer.getExpiresAt());
                    }
                    if (offer.getStatus() == OfferStatus.ACCEPTED) {
                        return offer.getCheckoutGroupId() == null
                                && offer.getAcceptedExpiresAt() != null
                                && now.isBefore(offer.getAcceptedExpiresAt());
                    }
                    return false;
                })
                .map(offerMapper::toOfferResponse)
                .map(this::enrichSingle)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public OfferResponse getOffer(UUID id){
        var user = currentUser.findCurrentUser();
        var offer = offerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.OFFER_NOT_EXISTED));
        if(!isParticipant(offer, user.getId())){
            throw new AppException(ErrorCode.OFFER_NOT_EXISTED);
        }
        return enrichSingle(offerMapper.toOfferResponse(offer));
    }
    @Transactional(readOnly = true)
    public PageResponse<OfferResponse> getHistory(int page, int size, UUID id){
        var user = currentUser.findCurrentUser();
        var offer = offerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.OFFER_NOT_EXISTED));
        if (!isParticipant(offer, user.getId())) {
            throw new AppException(ErrorCode.OFFER_NOT_EXISTED);
        }
        Sort sort = Sort.by(Sort.Order.asc("createdAt"),
                Sort.Order.asc("id"));
        Pageable pageable = createPageable(page, size, sort);

        var pageData = offerRepository.findAllByNegotiationId(offer.getNegotiationId(), pageable);
        List<OfferResponse> enriched = enrichList(
                pageData.getContent().stream().map(offerMapper::toOfferResponse).toList()
        );
        return PageResponse.<OfferResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElements(pageData.getTotalElements())
                .data(enriched)
                .build();
    }

    //get negotiations initiated by the current buyer
    @Transactional(readOnly = true)
    public PageResponse<OfferResponse> getSentOffers(int page, int size){
        var user = currentUser.findCurrentUser();

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
        Pageable pageable = createPageable(page, size, sort);
        var pageData = offerRepository.findAllByBuyerIdAndStatusNot(
                user.getId(),
                OfferStatus.SUPERSEDED,
                pageable
        );
        List<OfferResponse> data = enrichList(
                pageData.getContent()
                        .stream()
                        .map(offerMapper::toOfferResponse)
                        .toList()
        );
        return PageResponse.<OfferResponse>builder()
                .currentPage(pageData.getNumber() + 1)
                .pageSize(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElements(pageData.getTotalElements())
                .data(data)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<OfferResponse> getReceivedOffers(int page, int size){
        var user = currentUser.findCurrentUser();

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );

        Pageable pageable = createPageable(page, size, sort);
        var accessibleStoreId = storeAccessGuard.findAccessibleStoreId(
                user.getId(),
                PermissionConstants.OFFER_HANDLE
        );
        var pageData = accessibleStoreId
                .map(storeId -> offerRepository.findAllByStoreIdAndStatusNot(
                        storeId,
                        OfferStatus.SUPERSEDED,
                        pageable
                ))
                .orElseGet(() -> offerRepository.findAllBySellerIdAndStatusNot(
                        user.getId(),
                        OfferStatus.SUPERSEDED,
                        pageable
                ));
        List<OfferResponse> data = enrichList(
                pageData.getContent()
                        .stream()
                        .map(offerMapper::toOfferResponse)
                        .toList()
        );
        return PageResponse.<OfferResponse>builder()
                .currentPage(page)
                .pageSize(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElements(pageData.getTotalElements())
                .data(data)
                .build();
    }

    @Transactional
    public void expirePendingOffers() {
        Instant now = Instant.now();

        List<Offer> expiredOffers = offerRepository.findAllByStatusAndExpiresAtLessThan(
                OfferStatus.PENDING, now);

        for (Offer offer : expiredOffers) {
            offer.expire(now);
            applicationEventPublisher.publishEvent(
                    OfferEvent.builder()
                            .status(OfferStatus.EXPIRED)
                            .offer(offer)
                            .recipientId(
                                    offer.getSide() == OfferSide.BUYER
                                            ? offer.getSellerId()
                                            : offer.getBuyerId()
                            )
                            .build()
            );
        }

        List<Offer> expiredAccepted = offerRepository
                .findAllByStatusAndCheckoutGroupIdIsNullAndAcceptedExpiresAtLessThan(
                        OfferStatus.ACCEPTED, now);

        for (Offer offer : expiredAccepted) {
            offer.expireAccepted(now);
            applicationEventPublisher.publishEvent(
                    OfferEvent.builder()
                            .status(OfferStatus.EXPIRED)
                            .offer(offer)
                            .recipientId(offer.getBuyerId())
                            .build()
            );
        }
    }

    //helpers
    private Pageable createPageable(int page, int size, Sort sort){
        if(page < 1){
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        if(size < 1 || size > maxPageSize){
            throw new AppException(ErrorCode.INVALID_PAGE_SIZE);
        }
        return PageRequest.of(page - 1, size, sort);
    }
    private boolean isParticipant(Offer offer, UUID userId){
        return offer.getBuyerId().equals(userId) || canHandleAsSeller(offer, userId);
    }
    private void validateOfferPrice(BigDecimal price){
        if(price == null || price.compareTo(BigDecimal.ZERO) <= 0){
            throw new AppException(ErrorCode.INVALID_OFFER_PRICE);
        }
    }
    private void validateInitialOfferPrice(BigDecimal price, BigDecimal originalPrice) {

        validateOfferPrice(price);

        BigDecimal minimumPrice =
                originalPrice.multiply(minPricePercent);

        BigDecimal maximumPrice =
                originalPrice.multiply(maxPricePercent);

        if (price.compareTo(minimumPrice) < 0
                || price.compareTo(maximumPrice) >= 0) {

            throw new AppException(
                    ErrorCode.INVALID_OFFER_PRICE
            );
        }
    }
    private void validateCounterPrice(Offer previousOffer, OfferSide actor, BigDecimal price) {

        validateOfferPrice(price);

        Offer grandOffer = null;

        if (previousOffer.getPreviousOfferId() != null) {

            grandOffer =
                    offerRepository.findById(
                            previousOffer.getPreviousOfferId()
                    ).orElseThrow(
                            () -> new AppException(
                                    ErrorCode.OFFER_INVALID_STATE
                            )
                    );
        }

        BigDecimal lower;
        BigDecimal upper;

        if (actor == OfferSide.SELLER) {

            lower = previousOffer.getPrice();

            upper = grandOffer != null ? grandOffer.getPrice() : previousOffer.getOriginalPrice();

        } else {

            /*
             * A buyer can only counter a seller's offer.
             * Therefore, there must be a previous offer
             * belonging to the buyer.
             */
            if (grandOffer == null) {
                throw new AppException(ErrorCode.OFFER_INVALID_STATE);
            }

            lower = grandOffer.getPrice();
            upper = previousOffer.getPrice();
        }
        if (price.compareTo(lower) <= 0
                || price.compareTo(upper) >= 0) {

            throw new AppException(ErrorCode.INVALID_OFFER_PRICE);
        }
    }


    private void validateProductAvailable(UUID productId) {

        var product = productRepository.findById(productId)
                        .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getStatus().equals(ProductStatus.ACTIVE)) {
            throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE);
        }

        if (!product.isAllowOffer()) {
            throw new AppException(ErrorCode.OFFER_NOT_ALLOWED);
        }
    }

    private boolean canHandleAsSeller(Offer offer, UUID userId) {
        if (offer.getStoreId() != null) {
            return storeAccessGuard.hasStorePermission(
                    offer.getStoreId(),
                    userId,
                    PermissionConstants.OFFER_HANDLE
            );
        }
        return offer.getSellerId().equals(userId);
    }

    private OfferSide getActorSide(Offer offer, UUID userId){
        if (offer.getBuyerId().equals(userId)) return OfferSide.BUYER;
        if (canHandleAsSeller(offer, userId)) return OfferSide.SELLER;
        throw new AppException(ErrorCode.OFFER_NOT_EXISTED);
    }
    private Offer getOfferForUpdate(UUID offerId){
        return offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new AppException(ErrorCode.OFFER_NOT_EXISTED));
    }

    private OfferResponse enrichSingle(OfferResponse response) {
        if (response == null) return null;
        return enrichList(List.of(response)).get(0);
    }

    private List<OfferResponse> enrichList(List<OfferResponse> responses) {
        if (responses == null || responses.isEmpty()) return List.of();
        Set<UUID> productIds = responses.stream()
                .map(OfferResponse::productId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> buyerIds = responses.stream()
                .map(OfferResponse::buyerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (productIds.isEmpty() && buyerIds.isEmpty()) {
            return responses;
        }

        Map<UUID, Product> productsById = new HashMap<>();
        if (!productIds.isEmpty()) {
            List<Product> products = productRepository.findAllById(productIds);
            if (products != null) {
                for (Product p : products) {
                    if (p != null && p.getId() != null) productsById.put(p.getId(), p);
                }
            }
        }

        Map<UUID, User> buyersById = new HashMap<>();
        if (!buyerIds.isEmpty() && userRepository != null) {
            List<User> buyers = userRepository.findAllById(buyerIds);
            if (buyers != null) {
                for (User u : buyers) {
                    if (u != null && u.getId() != null) buyersById.put(u.getId(), u);
                }
            }
        }

        List<UUID> firstFileIds = productsById.values().stream()
                .map(Product::getImageFileIds)
                .filter(ids -> ids != null && !ids.isEmpty())
                .map(ids -> ids.get(0))
                .distinct()
                .toList();

        Map<UUID, String> mediaUrlByFileId = new HashMap<>();
        if (!firstFileIds.isEmpty() && fileStorageService != null) {
            List<MediaViewResponse> views = fileStorageService.getMediaViews(firstFileIds);
            if (views != null) {
                for (MediaViewResponse view : views) {
                    if (view != null && view.id() != null && view.viewUrl() != null) {
                        mediaUrlByFileId.put(view.id(), view.viewUrl());
                    }
                }
            }
        }

        return responses.stream().map(r -> {
            Product product = r.productId() != null ? productsById.get(r.productId()) : null;
            User buyer = r.buyerId() != null ? buyersById.get(r.buyerId()) : null;
            String title = product != null ? product.getTitle() : r.productTitle();
            String image = resolveProductImage(product, mediaUrlByFileId);
            if (image == null) image = r.productImage();
            String buyerName = buyer != null ? buyer.getFullName() : r.buyerName();
            if (Objects.equals(title, r.productTitle())
                    && Objects.equals(image, r.productImage())
                    && Objects.equals(buyerName, r.buyerName())) {
                return r;
            }
            return r.withMetadata(title, image, buyerName);
        }).toList();
    }

    private String resolveProductImage(Product product, Map<UUID, String> mediaUrlByFileId) {
        if (product == null) return null;
        if (product.getImageFileIds() != null && !product.getImageFileIds().isEmpty()) {
            String url = mediaUrlByFileId.get(product.getImageFileIds().get(0));
            if (url != null && !url.isBlank()) return url;
        }
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            return product.getImages().get(0);
        }
        return null;
    }
}
