package com.moldy.moldymarket.payment.mapper;

import com.moldy.moldymarket.payment.entity.PaymentTransaction;
import com.moldy.moldymarket.payment.webhook.IncomingTransfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentTransactionMapper {
    @Mapping(target = "transactionDate", source = "transactionTime")
    @Mapping(target = "transferType", source = "type")
    @Mapping(target = "transferAmount", source = "amount")
    PaymentTransaction toPaymentTransaction(IncomingTransfer incomingTransfer);
}
