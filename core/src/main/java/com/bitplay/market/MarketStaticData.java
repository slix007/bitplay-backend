package com.bitplay.market;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MarketStaticData {
    BITMEX("bitmex", 1),
    OKEX("okex", 2),
    LEFT_OKEX("okex", 3),
    POLONIEX("poloniex", 4),
    QUOINE("quoine", 5);

    private final String name;
    private final Integer id;
}
