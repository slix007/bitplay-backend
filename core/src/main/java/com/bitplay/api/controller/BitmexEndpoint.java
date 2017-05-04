package com.bitplay.api.controller;

import com.bitplay.api.domain.AccountInfoJson;
import com.bitplay.api.domain.OrderBookJson;
import com.bitplay.api.domain.OrderJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.api.service.BitplayUIServiceBitmex;
import com.bitplay.api.service.BitplayUIServiceOkCoin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Component
@Path("/market/bitmex")
public class BitmexEndpoint {

    @Autowired
    @Qualifier("Bitmex")
    private BitplayUIServiceBitmex bitmex;

    @GET
    @Path("/order-book")
    @Produces("application/json")
    public OrderBookJson okCoinOrderBook() {
        return this.bitmex.getOrderBook();
    }

    @GET
    @Path("/account")
    @Produces("application/json")
    public AccountInfoJson getAccountInfo() {
        return this.bitmex.getAccountInfo();
    }

    @POST
    @Path("/place-market-order")
    @Consumes("application/json")
    @Produces("application/json")
    public TradeResponseJson placeMarketOrder(TradeRequestJson tradeRequestJson) {
        return this.bitmex.doTrade(tradeRequestJson);
    }

    @GET
    @Path("/trade-history")
    @Produces("application/json")
    public List<VisualTrade> tradeHistory() {
        return this.bitmex.fetchTrades();
    }

    @GET
    @Path("/open-orders")
    @Produces("application/json")
    public List<OrderJson> openOrders() {
        return this.bitmex.getOpenOrders();
    }

    @POST
    @Path("/open-orders/move")
    @Produces("application/json")
    public ResultJson openOrders(OrderJson orderJson) {
        return this.bitmex.moveOpenOrder(orderJson);
    }

}

