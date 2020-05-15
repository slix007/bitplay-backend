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

    public String getSymbolForType(BitmexContractType bitmexContractType) {
        switch (bitmexContractType) {
            case XBTUSD_Perpetual:
                return "XBTUSD";
            case ETHUSD_Perpetual:
                return "ETHUSD";
            case XBTUSD_Quarter:
                return btcUsdQuoter;
            case XBTUSD_BiQuarter:
                return btcUsdBiQuoter;
            case ETHUSD_Quarter:
                return ethUsdQuoter;
        }
        return null;
    }

    public BitmexContractType parse(String contractTypeName) {
        if (contractTypeName.equals("XBTUSD")) {
            return BitmexContractType.XBTUSD_Perpetual;
        } else if (contractTypeName.equals("ETHUSD")) {
            return BitmexContractType.ETHUSD_Perpetual;
        } else if (contractTypeName.equals(btcUsdQuoter)) {
            return BitmexContractType.XBTUSD_Quarter;
        } else if (contractTypeName.equals(btcUsdBiQuoter)) {
            return BitmexContractType.XBTUSD_BiQuarter;
        } else if (contractTypeName.equals(ethUsdQuoter)) {
            return BitmexContractType.ETHUSD_Quarter;
        }
        return null;
    }
}
