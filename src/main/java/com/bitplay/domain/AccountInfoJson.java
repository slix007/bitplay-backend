package com.bitplay.domain;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
public class AccountInfoJson {
    private String btc;
    private String usd;

    private String raw;

    public AccountInfoJson(String btc, String usd, String raw) {
        this.btc = btc;
        this.usd = usd;
        this.raw = raw;
    }

    public String getBtc() {
        return btc;
    }

    public String getUsd() {
        return usd;
    }

    public String getRaw() {
        return raw;
    }
}
