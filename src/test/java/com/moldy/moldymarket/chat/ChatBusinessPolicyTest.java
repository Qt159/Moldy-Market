package com.moldy.moldymarket.chat;

import com.moldy.moldymarket.chat.entity.Conversation;
import com.moldy.moldymarket.chat.enums.ConversationStatus;
import com.moldy.moldymarket.chat.service.ChatAccessPolicy;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.store.entity.*;
import com.moldy.moldymarket.store.repository.StoreStaffRepository;
import com.moldy.moldymarket.user.entity.*;
import com.moldy.moldymarket.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatBusinessPolicyTest {
    UserRepository users = mock(UserRepository.class);
    StoreStaffRepository staff = mock(StoreStaffRepository.class);
    ChatAccessPolicy policy = new ChatAccessPolicy(users, staff);
    User first, second;
    Conversation room;
    Set<UUID> ids;

    @BeforeEach void fixtures() {
        first = user("Chu"); second = user("Nguyễn An");
        room = new Conversation(); room.setParticipant1Id(first.getId()); room.setParticipant2Id(second.getId());
        ids = Set.of(first.getId(), second.getId());
        when(users.findAllById(ids)).thenReturn(List.of(first, second));
        when(staff.findAllByUserIdIn(ids)).thenReturn(List.of());
    }
    @Test void loadsNamesInOneBatchWithoutInventingNamesForUnknownIds() {
        var context = policy.load(ids);
        assertThat(context.nameFor(first.getId())).isEqualTo("Chu");
        assertThat(context.nameFor(UUID.randomUUID())).isNull();
        assertThat(context.denialFor(room)).isNull();
        verify(users, times(1)).findAllById(ids);
    }
    @Test void ownStaffAndOwnerPairIsReadOnlyInBothDirections() {
        var shop = new Store(first.getId(), "Policy shop", "", "");
        when(staff.findAllByUserIdIn(ids)).thenReturn(List.of(new StoreStaff(shop, second, Set.of())));
        assertThat(policy.load(ids).denialFor(room)).isEqualTo(ErrorCode.CHAT_STORE_MEMBER);
        room.setParticipant1Id(second.getId()); room.setParticipant2Id(first.getId());
        assertThat(policy.load(ids).denialFor(room)).isEqualTo(ErrorCode.CHAT_STORE_MEMBER);
    }
    @Test void staffOfAnotherShopMayChatAndFormerStaffCanChatAgain() {
        var other = new Store(UUID.randomUUID(), "Other", "", "");
        when(staff.findAllByUserIdIn(ids)).thenReturn(List.of(new StoreStaff(other, second, Set.of())));
        assertThat(policy.load(ids).denialFor(room)).isNull();
        when(staff.findAllByUserIdIn(ids)).thenReturn(List.of());
        assertThat(policy.load(ids).denialFor(room)).isNull();
    }
    @Test void unavailableParticipantsAndClosedRoomsCannotSend() {
        second.setStatus(UserStatus.LOCKED);
        assertThat(policy.load(ids).denialFor(room)).isEqualTo(ErrorCode.CHAT_PARTICIPANT_UNAVAILABLE);
        second.setStatus(UserStatus.ACTIVE); room.setStatus(ConversationStatus.CLOSED);
        assertThat(policy.load(ids).denialFor(room)).isEqualTo(ErrorCode.INVALID_REQUEST);
    }
    @Test void legacyMultipleStaffRowsCannotHideMembershipInTheFirstShop() {
        var own = new Store(first.getId(), "Own", "", "");
        var other = new Store(UUID.randomUUID(), "Other", "", "");
        when(staff.findAllByUserIdIn(ids)).thenReturn(List.of(new StoreStaff(own, second, Set.of()), new StoreStaff(other, second, Set.of())));
        assertThat(policy.load(ids).denialFor(room)).isEqualTo(ErrorCode.CHAT_STORE_MEMBER);
    }
    private User user(String name) {
        var user = new User(); user.setId(UUID.randomUUID()); user.setFullName(name); user.setStatus(UserStatus.ACTIVE); return user;
    }
}
