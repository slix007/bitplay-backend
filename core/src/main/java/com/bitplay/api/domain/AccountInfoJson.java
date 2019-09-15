package com.bitplay.api.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@Getter
@AllArgsConstructor
public class AccountInfoJson {
    private String btc;
    private String usd;

    private String wallet;
    private String available;
    private String margin;
    private String positionStr;
    private String upl;
    private String leverage;
    private String availableForLong;
    private String availableForShort;
    private String longAvailToClose;
    private String shortAvailToClose;
    private String quAvg;
    private String ethBtcBid1;
    private String liqPrice;
    private String eMark;
    private String eBest;
    private String eLast;
    private String eAvg;
    private String entryPrice;

    private String raw;

    private String plPos; // okex only:

    public AccountInfoJson() {
    }

    public AccountInfoJson(String btc, String usd, String raw) {
        this.btc = btc;
        this.usd = usd;
        this.raw = raw;
    }

    public AccountInfoJson(String wallet, String available, String margin, String positionStr, String upl, String leverage,
            String availableForLong, String availableForShort, String longAvailToClose, String shortAvailToClose,
            String quAvg, String ethBtcBid1, String liqPrice, String eMark,
            String eLast, String eBest, String eAvg, String entryPrice, String raw,
            String plPos) {
        this.wallet = wallet;
        this.available = available;
        this.margin = margin;
        this.positionStr = positionStr;
        this.upl = upl;
        this.leverage = leverage;
        this.availableForLong = availableForLong;
        this.availableForShort = availableForShort;
        this.longAvailToClose = longAvailToClose;
        this.shortAvailToClose = shortAvailToClose;
        this.quAvg = quAvg;
        this.ethBtcBid1 = ethBtcBid1;
        this.liqPrice = liqPrice;
        this.eMark = eMark;
        this.eLast = eLast;
        this.eBest = eBest;
        this.eAvg = eAvg;
        this.entryPrice = entryPrice;
        this.raw = raw;
        this.plPos = plPos;
    }

    public static AccountInfoJson error() {
        return new AccountInfoJson("error","error", "error","error", "error", "error", "error", "error", "error", "error",
                "error", "error", "error", "error", "error", "error", "error", "error", "error", "error");
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
