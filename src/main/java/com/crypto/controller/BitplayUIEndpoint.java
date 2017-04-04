package com.crypto.controller;

import com.crypto.model.VisualTrade;
import com.crypto.service.AbstractBitplayUIService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

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
    @Qualifier("Poloniex")
    private AbstractBitplayUIService poloniex;

//    @Autowired
//    @Qualifier("OkCoin")
//    private AbstractBitplayUIService okCoin;

    @GET
    @Path("/")
    public String message() {
        return "Hello";
    }

    @GET
    @Path("/poloniex/order-book")
    @Produces("application/json")
    public List<VisualTrade> poloniexOrderBook() {
        return this.poloniex.fetchTrades();
    }

    @GET
    @Path("/okcoin/order-book")
    @Produces("application/json")
    public List<VisualTrade> okCoinOrderBook() {
        return null;
//        return this.okCoin.fetchTrades();
    }
}

