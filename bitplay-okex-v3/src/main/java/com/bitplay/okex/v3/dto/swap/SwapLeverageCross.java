package com.bitplay.okex.v3.dto.swap;

import lombok.Data;

@Data
public class SwapLeverageCross {

    private final String leverage;
    private final String side;// '1’. Fixed-margin for long position; ‘2’. Fixed-margin for short position; ‘3’. Crossed-margin
}
