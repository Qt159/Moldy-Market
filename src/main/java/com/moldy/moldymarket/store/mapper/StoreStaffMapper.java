package com.moldy.moldymarket.store.mapper;

import com.moldy.moldymarket.store.dto.response.StaffResponse;
import com.moldy.moldymarket.store.entity.StoreStaff;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StoreStaffMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "storeId", source = "store.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    StaffResponse toResponse(StoreStaff staff);
}
