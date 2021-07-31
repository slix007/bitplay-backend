package com.bitplay.okex.v5.dto;

import lombok.Data;

@Data
public class SwapLeverageCross {

    private final String leverage;
    private final String side;// '1’. Fixed-margin for long position; ‘2’. Fixed-margin for short position; ‘3’. Crossed-margin
}
