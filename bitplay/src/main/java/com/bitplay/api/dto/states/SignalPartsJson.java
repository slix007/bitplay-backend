package com.bitplay.api.dto.states;

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
    private Status btmOrderBook; // bid<ask
    private Status btmOrderBookXBTUSD; // bid<ask : extra OB while Eth
    private Status okOrderBook; // bid<ask
    private Status obTsDiffs;

}
