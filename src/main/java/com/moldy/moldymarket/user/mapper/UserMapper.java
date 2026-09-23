package com.moldy.moldymarket.user.mapper;

import com.moldy.moldymarket.user.dto.RegisterRequest;
import com.moldy.moldymarket.user.dto.RegisterResponse;
import com.moldy.moldymarket.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
