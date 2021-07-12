package com.bitplay.xchangestream.bitmex.dto;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Sergey Shurmin on 5/16/17.
 */
public class AuthenticateRequest {

    private String op = "authKey";
    private List<Object> args;

    public AuthenticateRequest(String apiKey, String nonce, String signature) {
        this.args = Arrays.asList(apiKey, Long.valueOf(nonce), signature);
    }

    public String getOp() {
        return op;
    }

    public List<Object> getArgs() {
        return args;
    }
}