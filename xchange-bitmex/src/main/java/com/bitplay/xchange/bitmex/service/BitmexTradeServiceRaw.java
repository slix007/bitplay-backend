package com.bitplay.xchange.bitmex.service;

import com.bitplay.xchange.Exchange;

import java.io.IOException;

/**
 * Created by Sergey Shurmin on 5/18/17.
 */
public class BitmexTradeServiceRaw extends BitmexBaseService {

    protected BitmexTradeServiceRaw(Exchange exchange) {
        super(exchange);
    }

    public void trade(String symbol,
                      String side,
                      String rate,
                      String amount) throws IOException {

//        bitmexAuthenitcatedApi.order(exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
//                symbol,
//                side,
//                "Limit",
//
//
//                rate, amount);
//        return;
    }


}
