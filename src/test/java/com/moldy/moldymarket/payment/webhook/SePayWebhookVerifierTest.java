package com.moldy.moldymarket.payment.webhook;

import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.payment.webhook.sepay.SePayWebhookVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;

class SePayWebhookVerifierTest {
    SePayWebhookVerifier verifier=new SePayWebhookVerifier();
    static final String SECRET="checkout-test-secret", BODY="{\"id\":1}";
    @BeforeEach void setup() { ReflectionTestUtils.setField(verifier,"hmacSecret",SECRET); }
    @Test void acceptsTheOfficialTimestampAndRawBodySignature() throws Exception {
        assertThatCode(()->verifier.verify(BODY,headers(Long.toString(Instant.now().getEpochSecond()),BODY)))
                .doesNotThrowAnyException();
    }
    @Test void changingTheBodyInvalidatesTheSignature() throws Exception {
        var headers=headers(Long.toString(Instant.now().getEpochSecond()),BODY);
        assertUnauthorized(()->verifier.verify("{\"id\":2}",headers));
    }
    @Test void rejectsMissingAuthentication() { assertUnauthorized(()->verifier.verify(BODY,new HttpHeaders())); }
    @Test void malformedTimestampIsUnauthorizedRatherThanAnInternalError() throws Exception {
        var headers=headers("not-a-timestamp",BODY);
        assertUnauthorized(()->verifier.verify(BODY,headers));
    }
    @Test void staleAndOverflowingTimestampsAreUnauthorized() throws Exception {
        for(String timestamp:new String[]{Long.toString(Instant.now().getEpochSecond()-301),Long.toString(Long.MIN_VALUE)}) {
            var headers=headers(timestamp,BODY); assertUnauthorized(()->verifier.verify(BODY,headers));
        }
    }
    private void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).hasFieldOrPropertyWithValue("errorCode",ErrorCode.WEBHOOK_UNAUTHORIZED);
    }
    private HttpHeaders headers(String timestamp,String body) throws Exception {
        var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        var headers=new HttpHeaders(); headers.set("X-SePay-Timestamp",timestamp);
        headers.set("X-SePay-Signature","sha256="+HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8))));
        return headers;
    }
}
