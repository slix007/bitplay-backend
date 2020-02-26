package com.bitplay.market;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MarketStaticData {
    BITMEX("bitmex", 1),
    OKEX("okex", 2),
    POLONIEX("poloniex", 3),
    QUOINE("quoine", 4);

    private final String name;
    private final Integer id;
}
