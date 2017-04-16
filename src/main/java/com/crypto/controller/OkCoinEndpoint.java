package com.crypto.controller;

import com.crypto.model.AccountInfoJson;
import com.crypto.model.TradeRequest;
import com.crypto.model.OrderBookJson;
import com.crypto.model.TradeResponse;
import com.crypto.service.BitplayUIServiceOkCoin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Component
@Path("/market/okcoin")
public class OkCoinEndpoint {

    @Autowired
    @Qualifier("OkCoin")
    private BitplayUIServiceOkCoin okCoin;

    @GET
    @Path("/order-book")
    @Produces("application/json")
    public OrderBookJson okCoinOrderBook() {
        return this.okCoin.getOrderBook();
    }

    @GET
    @Path("/account")
    @Produces("application/json")
    public AccountInfoJson getAccountInfo() {
        return this.okCoin.getAccountInfo();
    }

    @POST
    @Path("/buy")
    @Consumes("application/json")
    @Produces("application/json")
    public TradeResponse buyBtc(TradeRequest tradeRequest) {
        return this.okCoin.doTrade(tradeRequest);
    }

}

