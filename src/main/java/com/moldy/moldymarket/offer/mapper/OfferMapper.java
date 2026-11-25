package com.moldy.moldymarket.offer.mapper;

import com.moldy.moldymarket.offer.dto.response.OfferResponse;
import com.moldy.moldymarket.offer.entity.Offer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OfferMapper {
    OfferResponse toOfferResponse(Offer offer);
}
