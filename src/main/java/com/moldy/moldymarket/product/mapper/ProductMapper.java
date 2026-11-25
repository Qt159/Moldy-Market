package com.moldy.moldymarket.product.mapper;

import com.moldy.moldymarket.product.dto.ProductResponse;
import com.moldy.moldymarket.product.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    // Dành cho chủ sở hữu và người kiểm duyệt: đầy đủ
    @Mapping(target = "media", ignore = true)
    ProductResponse toResponse(Product product);

    // Dành cho người ngoài: bỏ trường nội bộ, và không lộ ID nhân viên của shop
    @Mapping(target = "sellerId", expression = "java(product.belongsToStore() ? null : product.getSellerId())")
    @Mapping(target = "removalReason", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "media", ignore = true)
    ProductResponse toPublicResponse(Product product);

}
