package com.bitplay.arbitrage.dto;

public enum ArbType {
    LEFT, RIGHT;

    public String s() {
        if (this == LEFT) {
            return "L";
        }
        return "R";
    }

    public String sLower() {
        if (this == LEFT) {
            return "l";
        }
        return "r";
    }

}
