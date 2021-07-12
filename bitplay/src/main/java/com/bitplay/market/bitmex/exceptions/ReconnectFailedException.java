package com.bitplay.market.bitmex.exceptions;

public class ReconnectFailedException extends Exception {

    public ReconnectFailedException() {
        super();
    }

    public ReconnectFailedException(String message) {
        super(message);
    }

    public ReconnectFailedException(Throwable cause) {
        super(cause);
    }
}
