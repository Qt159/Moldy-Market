package com.moldy.moldymarket.user.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.security.RoleConstants;
import com.moldy.moldymarket.security.refreshToken.service.RefreshTokenService;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.user.dto.request.AdminUserFilter;
import com.moldy.moldymarket.user.dto.request.LockUserRequest;
import com.moldy.moldymarket.user.dto.request.UnlockUserRequest;
import com.moldy.moldymarket.user.dto.response.*;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.entity.UserStatusHistory;
import com.moldy.moldymarket.user.event.UserAccountStatusChangedEvent;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.user.repository.UserSpecifications;
import com.moldy.moldymarket.user.repository.UserStatusHistoryRepository;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserService {

    static Set<OrderStatus> OPEN_ORDER_STATUSES = EnumSet.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PENDING_CONFIRMED,
            OrderStatus.CONFIRMED,
            OrderStatus.SHIPPING,
            OrderStatus.DELIVERED,
            OrderStatus.DISPUTED
    );

    static Set<String> SORTABLE_FIELDS =  Set.of("createdAt", "fullName", "email", "trustPoints");

    UserRepository userRepository;
    CurrentUser currentUser;
    UserStatusHistoryRepository historyRepository;
    UserRoleRepository userRoleRepository;
    StoreRepository storeRepository;
    OrderRepository orderRepository;
    RefreshTokenService refreshTokenService;
    ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public PagedResponse<AdminUserSummaryResponse> searchUsers(AdminUserFilter filter, Pageable pageable){
        AdminUserFilter f = filter == null ? new AdminUserFilter(null, null, null) : filter;

        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (f.keyword() != null && !f.keyword().isBlank()) {
            spec = spec.and(UserSpecifications.keyword(f.keyword()));
        }
        if (f.status() != null) spec = spec.and(UserSpecifications.hasStatus(f.status()));
        if (f.role() != null && !f.role().isBlank()) spec = spec.and(UserSpecifications.hasRole(f.role()));

        Page<User> page = userRepository.findAll(spec, safePageable(pageable));
        UUID actorId = currentUser.findCurrentUser().getId();
        Map<UUID, List<String>> rolesByUser = loadRoles(page.getContent().stream().map(User::getId).toList());

        List<AdminUserSummaryResponse> content = page.getContent().stream().map(u -> {
            List<String> roles = rolesByUser.getOrDefault(u.getId(), List.of());
            return new AdminUserSummaryResponse(
                    u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getStatus(),
                    u.getTrustPoints(), roles, u.getCreatedAt(), u.getLockedAt(),
                    canLock(u, roles, actorId), canUnlock(u, actorId));
        }).toList();

        return PagedResponse.of(page, content, pageable);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID userId){
        User user = findUser(userId);
        UUID actorId = currentUser.findCurrentUser().getId();
        return toDetail(user, loadRoles(List.of(userId)).getOrDefault(userId, List.of()), actorId);
    }

    @Transactional(readOnly = true)
    public List<UserStatusHistoryResponse> getStatusHistory(UUID userId){
        findUser(userId);
        List<UserStatusHistory> histories = historyRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        Map<UUID, ActorRef> actors = loadActors(histories.stream().map(UserStatusHistory::getActorId).toList());

        return histories.stream().map(h -> new UserStatusHistoryResponse(
                h.getId(), h.getFromStatus(), h.getToStatus(), h.getReason(),
                actors.get(h.getActorId()), h.getCreatedAt()
        )).toList();
    }

    @Transactional
    public AdminUserDetailResponse lockUser(UUID userId, LockUserRequest request){
        User actor = currentUser.findCurrentUser();
        User targetUser = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        List<String> roles = loadRoles(List.of(userId)).getOrDefault(userId, List.of());
        assertNotSelf(actor, targetUser);
        if(roles.contains(RoleConstants.ADMIN)) throw new AppException(ErrorCode.USER_CANNOT_LOCK_ADMIN);

        switch (targetUser.getStatus()) {
            case LOCKED -> throw new AppException(ErrorCode.USER_ALREADY_LOCKED);
            case PENDING_VERIFICATION -> throw new AppException(ErrorCode.USER_PENDING_VERIFICATION);
            case ACTIVE -> {//valid
            }
        }

        Instant now = Instant.now();
        String cleanReason = request.reason().trim();
        targetUser.lock(actor.getId(), cleanReason, now);
        userRepository.save(targetUser);

        //access token is prevented at JwtAuthenticationFilter
        //recall refresh so that users can not extend the session anymore
        refreshTokenService.revokeAllForUser(targetUser);
        recordAndPublish(targetUser.getId(), UserStatus.ACTIVE, UserStatus.LOCKED, cleanReason, actor.getId(), now);
        return toDetail(targetUser, roles, actor.getId());
    }

    @Transactional
    public AdminUserDetailResponse unlockUser(UUID userId, UnlockUserRequest request) {
        User actor = currentUser.findCurrentUser();
        User targetUser = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        assertNotSelf(actor, targetUser);
        if (targetUser.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.USER_PENDING_VERIFICATION);
        }
        if (targetUser.getStatus() != UserStatus.LOCKED) throw new AppException(ErrorCode.USER_NOT_LOCKED);


        Instant now = Instant.now();
        targetUser.unlock();
        userRepository.save(targetUser);

        String cleanNote = (request.note() == null || request.note().isBlank()) ? null : request.note().trim();
        recordAndPublish(targetUser.getId(), UserStatus.LOCKED, UserStatus.ACTIVE, cleanNote, actor.getId(), now);
        return toDetail(targetUser, loadRoles(List.of(userId)).getOrDefault(userId, List.of()), actor.getId());
    }

    @Transactional
    public AdminUserDetailResponse changeStatusLegacy(UUID userId, UserStatus status) {
        return switch (status) {
            case LOCKED -> lockUser(userId, new LockUserRequest("lock through API PUT api/v1/users/{id}/status (no reason)"));
            case ACTIVE -> unlockUser(userId, null);
            default -> throw new AppException(ErrorCode.USER_STATUS_TRANSITION_INVALID);
        };
    }

    //helpers
    private void recordAndPublish(UUID userId, UserStatus from, UserStatus to,
                                  String reason, UUID actorId, Instant now) {
        UserStatusHistory saved = historyRepository.save(
                UserStatusHistory.builder()
                        .userId(userId)
                        .fromStatus(from)
                        .toStatus(to)
                        .reason(reason)
                        .actorId(actorId)
                        .createdAt(now)
                        .build());
        applicationEventPublisher.publishEvent(new UserAccountStatusChangedEvent(saved.getId(), userId, to, reason, now));
    }
    private AdminUserDetailResponse toDetail(User user, List<String> roles, UUID actorId) {
       LockInfo lock = null;
        if (user.getStatus() == UserStatus.LOCKED) {
            ActorRef lockedBy = user.getLockedBy() == null ? null
                    : loadActors(List.of(user.getLockedBy())).get(user.getLockedBy());
            lock = new LockInfo(user.getLockReason(), user.getLockedAt(), lockedBy);
        }
        OwnedStore store = storeRepository.findByOwnerId(user.getId())
                .map(s -> new OwnedStore(s.getId(), s.getName(), s.getStatus()))
                .orElse(null);

        return new AdminUserDetailResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getAddress(),
                user.getStatus(), user.getTrustPoints(), roles, user.getCreatedAt(), user.getUpdatedAt(),
                lock, store,
                orderRepository.countByBuyerIdAndStatusIn(user.getId(), OPEN_ORDER_STATUSES),
                orderRepository.countBySellerIdAndStatusIn(user.getId(), OPEN_ORDER_STATUSES),
                canLock(user, roles, actorId), canUnlock(user, actorId));
    }
    private static boolean canLock(User u, List<String> roles, UUID actorId) {
        return u.getStatus() == UserStatus.ACTIVE
                && !roles.contains(RoleConstants.ADMIN)
                && !u.getId().equals(actorId);
    }

    private static boolean canUnlock(User u, UUID actorId) {
        return u.getStatus() == UserStatus.LOCKED && !u.getId().equals(actorId);
    }
    private static void assertNotSelf(User actor, User target) {
        if (actor.getId().equals(target.getId())) throw new AppException(ErrorCode.CANNOT_LOCK_YOURSELF);
    }
    private User findUser(UUID userId){
        return userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
    private Map<UUID, ActorRef> loadActors(Collection<UUID> ids) {
        Set<UUID> distinct = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) return Map.of();
        return userRepository.findAllById(distinct).stream()
                .map(u -> new ActorRef(u.getId(), u.getFullName(), u.getEmail()))
                .collect(Collectors.toMap(ActorRef::id, Function.identity()));
    }
    private Map<UUID, List<String>> loadRoles(Collection<UUID> userIds){
        if(userIds.isEmpty()) return Map.of();
        return userRoleRepository.findAllWithRoleByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        ur -> ur.getUser().getId(),
                        Collectors.mapping(ur -> ur.getRole().getName(),
                                Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new), List::copyOf))
                ));
    }
    private static Pageable safePageable(Pageable pageable) {
        Sort sort = Sort.by(pageable.getSort().stream()
                .filter(o -> SORTABLE_FIELDS.contains(o.getProperty()))
                .toList());
        if (sort.isUnsorted()) sort = Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

}
