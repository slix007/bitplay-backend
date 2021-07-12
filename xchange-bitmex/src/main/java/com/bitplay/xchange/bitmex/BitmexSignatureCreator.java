package com.bitplay.xchange.bitmex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

//import java.util.Base64;

/**
 * Created by Sergey Shurmin on 5/8/17.
 */
public class BitmexSignatureCreator {

    protected final String apikey;
    protected final String secretKey;

    public BitmexSignatureCreator(String apikey, String secretKey) {
        this.apikey = apikey;
        this.secretKey = secretKey;
    }

    public String generateBitmexSignature(String verb, String url, String nonce) {
        String hash = null;
        try {
            String message = verb + url + nonce;

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            final byte[] binaryData = sha256_HMAC.doFinal(message.getBytes());
            hash = DatatypeConverter.printHexBinary(binaryData);
            System.out.println(hash);
        } catch (Exception e) {
            System.out.println("Error");
        }

        return hash;
    }

}
