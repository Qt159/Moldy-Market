package com.moldy.moldymarket.cart.mapper;

import com.moldy.moldymarket.cart.dto.response.CartItemResponse;
import com.moldy.moldymarket.cart.dto.response.CartResponse;
import com.moldy.moldymarket.cart.dto.response.CartSummaryResponse;
import com.moldy.moldymarket.cart.entity.Cart;
import com.moldy.moldymarket.cart.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CartMapper {

    // CartItem entity thành CartItemResponse DTO
    
    CartItemResponse toCartItemResponse(CartItem cartItem);

    // danh sách CartItem entity thành danh sách CartItemResponse DTO
    List<CartItemResponse> toCartItemResponseList(List<CartItem> cartItems);

    // Cart entity thành CartResponse DTO
    @Mapping(source = "user.id", target = "userId")
    CartResponse toCartResponse(Cart cart);

    // Cart entity thành CartSummaryResponse DTO
    CartSummaryResponse toCartSummaryResponse(Cart cart);
}
