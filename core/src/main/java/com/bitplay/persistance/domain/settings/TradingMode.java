package com.bitplay.persistance.domain.settings;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TradingMode {
    CURRENT("current"),
    VOLATILE("volatile"),
    CURRENT_VOLATILE("current-volatile"); // only for fplayTrade logs in DB and dealPrices

    private String fullName;
}
