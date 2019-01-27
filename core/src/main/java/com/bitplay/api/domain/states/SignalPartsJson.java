package com.bitplay.api.domain.states;

import lombok.Data;

@Data
public class SignalPartsJson {

    public enum Status {
        OK, WRONG, STARTED
    }

    private String deltaName;
    private Status signalDelay;
    private Status btmMaxDelta;
    private Status okMaxDelta;
    private Status ntUsd;
    private Status states;
    private Status btmDqlOpen;
    private Status okDqlOpen;
    private Status btmAffordable;
    private Status okAffordable;
    private Status priceLimits;

}
