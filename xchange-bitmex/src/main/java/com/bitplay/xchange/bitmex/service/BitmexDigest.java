package com.bitplay.xchange.bitmex.service;

import com.bitplay.xchange.service.BaseParamsDigest;
import com.bitplay.xchange.utils.DigestUtils;
import si.mazi.rescu.RestInvocation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.HeaderParam;
import javax.xml.bind.DatatypeConverter;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

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
        String nonce = restInvocation.getParamValue(HeaderParam.class, "api-expires").toString();
        String path = restInvocation.getInvocationUrl().split(restInvocation.getBaseUrl())[1];
        String payload =
                restInvocation.getHttpMethod() + path + nonce + restInvocation.getRequestBody();
        return digestString(payload);
    }

    public String digestString(String payload) {
        return DigestUtils.bytesToHex(getMac().doFinal(payload.getBytes())).toLowerCase();
    }

    public static String generateBitmexSignature(String secretKey, String signatureSource) throws InvalidKeyException, NoSuchAlgorithmException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        final byte[] binaryData = sha256_HMAC.doFinal(signatureSource.getBytes());
        return DatatypeConverter.printHexBinary(binaryData);
    }


}
