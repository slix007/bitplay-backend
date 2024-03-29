package com.bitplay.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 5/4/17.
 */
@Getter
@AllArgsConstructor
public class MarketList {
    String left;
    String right;
    String leftFutureContractName;
    String rightFutureContractName;
    boolean isEth;
    boolean isLeftSwap;
    boolean isRightSwap;
}
