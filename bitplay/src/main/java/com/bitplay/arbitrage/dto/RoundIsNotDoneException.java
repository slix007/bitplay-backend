package com.bitplay.arbitrage.dto;

/**
 * Created by Sergey Shurmin on 3/6/18.
 */
public class RoundIsNotDoneException extends Exception {
    public RoundIsNotDoneException(String s) {
        super(s);
    }
}
