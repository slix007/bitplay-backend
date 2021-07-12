package com.bitplay.arbitrage.posdiff;


import com.bitplay.arbitrage.dto.ArbType;
import lombok.Data;

@Data
public class RecoveryParam {

    private final String predefinedMarketNameWithType;
    private final ArbType killPosArbType;

    public RecoveryParam(String predefinedMarketNameWithType, ArbType killPosArbType) {
        this.predefinedMarketNameWithType = predefinedMarketNameWithType;
        this.killPosArbType = killPosArbType;
    }

    boolean isAuto() {
        return predefinedMarketNameWithType != null;
    }

    boolean isKpRight() {
        return isAuto() && killPosArbType == ArbType.RIGHT;
    }

    boolean isKpLeft() {
        return isAuto() && killPosArbType == ArbType.LEFT;
    }

}
