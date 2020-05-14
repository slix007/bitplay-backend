package com.bitplay.persistance.domain.settings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    private ContractType left;
    private ContractType right;

    public static ContractMode parse(String leftName, String rightName) {
        ContractType left = parseContractType(leftName);
        ContractType right = parseContractType(rightName);
        return new ContractMode(left, right);
    }

    public static ContractType parseContractType(String contractTypeName) {
        ContractType theType;
        if (contractTypeName.startsWith("_", 3)) { // BTC_*, ETH_* - okex
            theType = OkexContractType.valueOf(contractTypeName);
        } else {
            try {
                theType = BitmexContractType.valueOf(contractTypeName);
            } catch (IllegalArgumentException e) {
                // workaround for BitmexContractType change
                if (contractTypeName.startsWith("XBT")) {
                    theType = BitmexContractType.XBTUSD_Perpetual;
                } else { // ETH
                    theType = BitmexContractType.ETHUSD_Perpetual;
                }
            }
        }
        return theType;
    }

    public String getModeName() {
        return "L_" + left.getMarketName() + "_R_" + right.getMarketName();
    }

    public String getMainSetName() {
        return getModeName();
    }

    public boolean isEth() {
        return left.isEth() || right.isEth();
    }

    public Integer getModeScale() {
        return left.getScale() > right.getScale()
                ? left.getScale()
                : right.getScale();
    }


}
