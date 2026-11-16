package com.moldy.moldymarket.payment.webhook;

import org.springframework.http.HttpHeaders;

public interface WebhookProvider {
    String name();
    void verify(String rawBody, HttpHeaders headers);

    //parse provider's json into the common shape, throw webhook invalid request on bad input
    IncomingTransfer parse(String rawBody);

    Object successBody();
}
