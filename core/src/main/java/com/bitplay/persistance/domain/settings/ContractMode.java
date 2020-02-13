package com.bitplay.persistance.domain.settings;

import lombok.Data;

@Data
public class ContractMode {
//    MODE1_SET_BU11("M10", "set_bu11", BitmexContractType.XBTUSD, OkexContractType.BTC_ThisWeek),
//    MODE2_SET_BU12("M11", "set_bu12", BitmexContractType.XBTUSD, OkexContractType.BTC_NextWeek),
//    MODE3_SET_BU23("M20", "set_bu23", BitmexContractType.XBTH20, OkexContractType.BTC_Quarter),
//    MODE_TMP("TMP", "set_tmp", BitmexContractType.XBTZ19, OkexContractType.BTC_NextWeek),// NextWeek!!!
//    MODE4_SET_BU10_SET_EU11("M21", "set_eu11", BitmexContractType.ETHUSD, OkexContractType.ETH_ThisWeek),
//    MODE5_SET_BU10_SET_EU12("M22", "set_eu12", BitmexContractType.ETHUSD, OkexContractType.ETH_NextWeek),
//    BTC_SWAP_SET("BTC_SWAP", "BTC_SWAP", BitmexContractType.XBTUSD, OkexContractType.BTC_Swap),
//    ETH_SWAP_SET("ETH_SWAP", "ETH_SWAP", BitmexContractType.ETHUSD, OkexContractType.ETH_Swap),
//    ;

    private final ContractType left;
    private final ContractType right;

    public static ContractMode parse(String leftName, String rightName) {
        ContractType left = parseContractType(leftName);
        ContractType right = parseContractType(rightName);
        return new ContractMode(left, right);
    }

    private static ContractType parseContractType(String contractTypeName) {
        ContractType theType;
        if (contractTypeName.charAt(3) == '_') { // BTC_*, ETH_* - okex
            theType = OkexContractType.valueOf(contractTypeName);
        } else {
            theType = BitmexContractType.valueOf(contractTypeName);
        } return theType;
    }

    public String getModeName() {
        return "L_" + left.getMarketName() + "_R_" + right.getMarketName();
    }

    public String getMainSetName() {
        return getModeName();
    }

    //    private String mainSetName;
//    private BitmexContractType bitmexContractType;
//    private OkexContractType okexContractType;

//    public enum ContractModeSet {
//        set_bu11,
//        set_bu10,
//        set_bu12,
//        set_bu23,
//        set_eu11,
//        set_eu12,
//    }
    //Mod #1 - set_bu11; // set_bu11 пишется справа от Model #1
    //Mod #2 - set_bu12;
    //Mod #3 - set_bu23;
    //Mod #4 - set_bu10 + set_eu11;
    //Mod #5 - set_bu10 + set_eu12;

    //set_bu11: b = XBTUSD, o = BTC_W (BTC##), hedge_btc
    //set_bu10: b = XBTUSD, o = null, hedge_btc
    //set_bu12: b = XBTUSD, o = BTC_BW (BTC##), hedge_btc
    //set_bu23: b = XBT_Q, o = BTC_Q (BTC##), hedge_btc
    //set_eu11: b = ETHUSD, o = ETH_W, hedge_eth
    //set_eu12: b = ETHUSD, o = ETH_BW, hedge_eth

//    public static ContractMode parse(String left, String right) {
//        BitmexContractType bitmexContractType = BitmexContractType.valueOf(bitmex);
//        OkexContractType okexContractType = OkexContractType.valueOf(okex);
//        return ContractMode.parse(bitmexContractType, okexContractType);
//    }
//
//    public static ContractMode parse(BitmexContractType bitmexContractType, OkexContractType okexContractType) {
//        for (ContractMode value : ContractMode.values()) {
//            if (value.bitmexContractType == bitmexContractType
//                    && value.okexContractType == okexContractType) {
//                return value;
//            }
//        }
//        throw new IllegalArgumentException(String.format("Unknown mode bitmexContractType=%s, okexContractType=%s",
//                bitmexContractType,
//                okexContractType));
//    }

    public boolean isEth() {
        return left.isEth();
    }

    public String getExtraSetName() {
        return isEth() ? "set_bu10" : "";
    }

    public Integer getModeScale() {
        return left.getScale() > right.getScale()
                ? left.getScale()
                : right.getScale();
    }


}
