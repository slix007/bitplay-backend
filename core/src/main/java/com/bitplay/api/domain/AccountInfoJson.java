package com.bitplay.api.domain;

import lombok.Getter;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@Getter
public class AccountInfoJson {
    private String btc;
    private String usd;

    private String wallet;
    private String available;
    private String margin;
    private String position;
    private String upl;
    private String leverage;
    private String availableForLong;
    private String availableForShort;
    private String quAvg;
    private String ethBtcBid1;
    private String liqPrice;
    private String eMark;
    private String eBest;
    private String eLast;
    private String eAvg;
    private String entryPrice;

    private String raw;

    public AccountInfoJson() {
    }

    public AccountInfoJson(String btc, String usd, String raw) {
        this.btc = btc;
        this.usd = usd;
        this.raw = raw;
    }

    public AccountInfoJson(String wallet, String available, String margin, String position, String upl, String leverage,
            String availableForLong, String availableForShort, String quAvg, String ethBtcBid1, String liqPrice, String eMark,
            String eLast, String eBest, String eAvg, String entryPrice, String raw) {
        this.wallet = wallet;
        this.available = available;
        this.margin = margin;
        this.position = position;
        this.upl = upl;
        this.leverage = leverage;
        this.availableForLong = availableForLong;
        this.availableForShort = availableForShort;
        this.quAvg = quAvg;
        this.ethBtcBid1 = ethBtcBid1;
        this.liqPrice = liqPrice;
        this.eMark = eMark;
        this.eLast = eLast;
        this.eBest = eBest;
        this.eAvg = eAvg;
        this.entryPrice = entryPrice;
        this.raw = raw;
    }

    public String geteMark() {
        return eMark;
    }

    public String geteLast() {
        return eLast;
    }

    public String geteBest() {
        return eBest;
    }

    public String geteAvg() {
        return eAvg;
    }

}
