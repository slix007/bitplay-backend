package com.bitplay.xchange.bitmex.service;

import com.bitplay.xchange.bitmex.dto.BitmexInfoDto;
import com.bitplay.xchange.Exchange;

import java.io.IOException;
import java.util.List;

import io.swagger.client.ApiException;
import io.swagger.client.model.OrderBookL2;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexMarketDataServiceRaw extends BitmexBaseService {

    /**
     * Constructor
     */
    protected BitmexMarketDataServiceRaw(Exchange exchange) {
        super(exchange);
    }


//    public PaymiumTicker getPaymiumTicker() throws IOException {
//
//        return Paymium.getPaymiumTicker();
//    }

    public List<OrderBookL2> getBitmexMarketDepth(String symbol, Integer depth) throws IOException, ApiException {

        return bitmexPublicApi.getOrderBook(
                symbol,
                depth
        );
    }

    public BitmexInfoDto getBitmexInfoDto() throws IOException {
        return bitmexPublicApi.getGenralInfo();
    }

//    public PaymiumTrade[] getPaymiumTrades() throws IOException {
//
//        return Paymium.getTrades();
//    }

}
