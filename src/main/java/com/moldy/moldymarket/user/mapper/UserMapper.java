package com.moldy.moldymarket.user.mapper;

import com.moldy.moldymarket.user.dto.request.RegisterRequest;
import com.moldy.moldymarket.user.dto.response.RegisterResponse;
import com.moldy.moldymarket.user.dto.response.UserProfileReSponse;
import com.moldy.moldymarket.user.dto.response.UserSummaryResponse;
import com.moldy.moldymarket.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "googleId", ignore = true)
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "trustPoints", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "bankName", ignore = true)
    @Mapping(target = "bankAccountNumber", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(RegisterRequest request);

    @Mapping(source = "id", target = "userId")
    RegisterResponse toRegisterResponse(User user);

    UserSummaryResponse toSummaryResponse(User user);

    default UserProfileReSponse toProfileResponse(User user, List<String> roles) {
        return toProfileResponse(user, roles, null, null);
    }

    default UserProfileReSponse toProfileResponse(User user, List<String> roles, List<String> permissions, UUID storeId) {
        if (user == null) return null;
        return new UserProfileReSponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getStatus(),
                user.getTrustPoints(),
                roles,
                permissions,
                storeId,
                user.getCreatedAt()
        );
    }
}
