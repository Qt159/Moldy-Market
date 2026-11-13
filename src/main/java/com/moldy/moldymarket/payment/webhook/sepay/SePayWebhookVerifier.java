package com.moldy.moldymarket.payment.webhook.sepay;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;


@Component
public class SePayWebhookVerifier {
    private static final long TOLERANCE_SECONDS = 300;

    @Value("${sePay.webhook.secret-key}")
    private String hmacSecret;

    public void verify(String rawBody, HttpHeaders headers){
        String signature = headers.getFirst("X-SePay-Signature");
        if(!hmacSecret.isBlank() && signature != null){
            verifyKey(rawBody, signature, headers.getFirst("X-SePay-TimeStamp"));
        }
        else throw unauthorized();
    }
    private void verifyKey(String rawBody, String signature, String timeStamp) {
        if(timeStamp == null) throw unauthorized();
        try{
            long ts = Long.parseLong(timeStamp);
            if(Math.abs(Instant.now().getEpochSecond() - ts) > TOLERANCE_SECONDS) throw unauthorized();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of()
                    .formatHex(mac.doFinal((ts + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
            if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) throw unauthorized();

        }catch (NoSuchAlgorithmException | InvalidKeyException ex){
            throw unauthorized();
        }
    }

    private static AppException unauthorized() {return new AppException(ErrorCode.WEBHOOK_UNAUTHORIZED);}
}
