package com.bitplay.service.ws;

public interface WsMessageHandler {

    void onMessage(String message);

    void onError(Throwable error);
}
