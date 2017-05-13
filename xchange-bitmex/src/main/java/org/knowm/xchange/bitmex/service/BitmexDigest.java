package org.knowm.xchange.bitmex.service;

import org.knowm.xchange.service.BaseParamsDigest;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.HeaderParam;
import javax.xml.bind.DatatypeConverter;

import si.mazi.rescu.RestInvocation;

/**
 * Created by Sergey Shurmin on 5/11/17.
 */
public class BitmexDigest extends BaseParamsDigest {

    protected final String apikey;
    protected final String secretKey;

    /**
     * Constructor
     *
     * @throws IllegalArgumentException if key is invalid (cannot be base-64-decoded or the decoded key is invalid).
     */
    private BitmexDigest(String apikey, String secretKey) {
        super(secretKey, HMAC_SHA_256);

        this.apikey = apikey;
        this.secretKey = secretKey;
    }

    public static BitmexDigest createInstance(String apikey, String secretKey) {

        try {
            if (apikey != null && secretKey != null)
                return new BitmexDigest(apikey, secretKey);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not decode Base 64 string", e);
        }
        return null;
    }

    @Override
    public String digestParams(RestInvocation restInvocation) {
        final String verb = restInvocation.getHttpMethod();
        final String path = restInvocation.getPath();
        final String pathWithQuery = path + "?" + restInvocation.getQueryString();
        final String nonce = restInvocation.getParamValue(HeaderParam.class, "api-nonce").toString();
        final String requestBody = restInvocation.getRequestBody();

        final String signatureSource = verb + pathWithQuery + nonce + requestBody;
        final String signature;
        try {
            signature = generateBitmexSignature(signatureSource);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Invalid key. Check it.");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Illegal algorithm for post body digest. Check the implementation.");
        }

        return signature;
    }

    private String generateBitmexSignature(String message) throws InvalidKeyException, NoSuchAlgorithmException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        final byte[] binaryData = sha256_HMAC.doFinal(message.getBytes());
        return DatatypeConverter.printHexBinary(binaryData);
    }
}
