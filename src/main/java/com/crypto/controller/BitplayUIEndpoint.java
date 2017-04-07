package com.crypto.controller;

import com.crypto.model.OrderBookJson;
import com.crypto.service.BitplayUIServiceOkCoin;
import com.crypto.service.BitplayUIServicePoloniex;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Component
@Path("/market")
public class BitplayUIEndpoint {

    @Autowired
    private BitplayUIServicePoloniex poloniex;

    @Autowired
    @Qualifier("OkCoin")
    private BitplayUIServiceOkCoin okCoin;

    @GET
    @Path("/")
    public String message() {
        return "Hello";
    }

    @GET
    @Path("/poloniex/order-book")
    @Produces("application/json")
    public OrderBookJson poloniexOrderBook() {
        return this.poloniex.getOrderBook();
    }

    @GET
    @Path("/okcoin/order-book")
    @Produces("application/json")
    public OrderBookJson okCoinOrderBook() {
        return this.okCoin.getOrderBook();
    }
}

