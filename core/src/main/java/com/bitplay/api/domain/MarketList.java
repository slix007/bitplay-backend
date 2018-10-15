package com.bitplay.api.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 5/4/17.
 */
@Getter
@AllArgsConstructor
public class MarketList {
    String first;
    String second;
    String firstFutureContractName;
    String secondFutureContractName;

    public boolean isEth() {
        return firstFutureContractName != null && firstFutureContractName.startsWith("ETH");
    }
}
