package com.bitplay.arbitrage.exceptions;

/**
 * Created by Sergey Shurmin on 4/8/18.
 */
public class NotYetInitializedException extends RuntimeException {

    public NotYetInitializedException() {
    }

    public NotYetInitializedException(String message) {
        super(message);
    }
}
