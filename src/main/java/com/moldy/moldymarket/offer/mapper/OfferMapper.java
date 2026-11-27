package com.moldy.moldymarket.offer.mapper;

import com.moldy.moldymarket.offer.dto.response.OfferResponse;
import com.moldy.moldymarket.offer.entity.Offer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OfferMapper {
    @Mapping(target = "productTitle", ignore = true)
    @Mapping(target = "productImage", ignore = true)
    @Mapping(target = "buyerName", ignore = true)
    OfferResponse toOfferResponse(Offer offer);
}
