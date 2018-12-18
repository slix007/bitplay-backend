package com.bitplay.persistance.domain.settings;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TradingMode {
    CURRENT("Current mode"),
    VOLATILE("Volatile mode");

    private String fullName;
}
