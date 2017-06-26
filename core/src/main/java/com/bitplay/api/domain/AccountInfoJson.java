package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
public class AccountInfoJson {
    private String btc;
    private String usd;

    private String wallet;
    private String available;
    private String equity;
    private String margin;
    private String position;

    private String raw;

    public AccountInfoJson() {
    }

    public AccountInfoJson(String btc, String usd, String raw) {
        this.btc = btc;
        this.usd = usd;
        this.raw = raw;
    }

    public AccountInfoJson(String wallet, String available, String equity, String margin, String position, String raw) {
        this.wallet = wallet;
        this.available = available;
        this.equity = equity;
        this.margin = margin;
        this.position = position;
        this.raw = raw;
    }

    public String getBtc() {
        return btc;
    }

    public String getUsd() {
        return usd;
    }

    public String getWallet() {
        return wallet;
    }

    public String getAvailable() {
        return available;
    }

    public String getEquity() {
        return equity;
    }

    public String getMargin() {
        return margin;
    }

    public String getPosition() {
        return position;
    }

    public String getRaw() {
        return raw;
    }
}
