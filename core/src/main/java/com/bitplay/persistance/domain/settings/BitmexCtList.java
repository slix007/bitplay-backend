package com.bitplay.persistance.domain.settings;

import lombok.Data;

@Data
public class BitmexCtList {

    private String btcUsdQuoter;
    private String btcUsdBiQuoter;
    private String ethUsdQuoter;

    public static BitmexCtList createDefault() {
        final BitmexCtList types = new BitmexCtList();
        types.btcUsdQuoter = "XBTM20";
        types.btcUsdBiQuoter = "XBTU20";
        types.ethUsdQuoter = "ETHM20";
        return types;
    }
}
