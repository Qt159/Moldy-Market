package com.moldy.moldymarket.store.mapper;

import com.moldy.moldymarket.store.dto.response.PublicStoreResponse;
import org.mapstruct.Mapper;
import com.moldy.moldymarket.store.dto.response.StoreResponse;
import com.moldy.moldymarket.store.entity.Store;

@Mapper(componentModel = "spring")
public interface StoreMapper {
    StoreResponse toResponse(Store store);
    PublicStoreResponse toPublicResponse(Store store);
}
