package com.bitplay.persistance.domain.settings;

import lombok.Data;

import java.util.Map;

@Data
public class BitmexContractTypes {

    private Map<BitmexContractType, String>
    private BitmexContractType perp = BitmexContractType.XBTUSD_Perpetual;

    public static BitmexContractTypes createDefault() {
        final BitmexContractTypes b = new BitmexContractTypes();
        b.

    }
}
