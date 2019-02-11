package com.bitplay.arbitrage.dto;

import lombok.Data;

/**
 * Created by Sergey Shurmin on 6/12/17.
 */
@Data
public class SignalTypeEx {

    private final SignalType signalType;
    private final String exName; // extra suffix for counterName

    public String getCounterName() {
        return signalType.getCounterName() + exName;
    }

}