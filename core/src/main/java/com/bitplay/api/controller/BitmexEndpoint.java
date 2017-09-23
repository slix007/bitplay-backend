package com.bitplay.api.controller;

import com.bitplay.api.domain.AccountInfoJson;
import com.bitplay.api.domain.ChangeRequestJson;
import com.bitplay.api.domain.FutureIndexJson;
import com.bitplay.api.domain.LiquidationInfoJson;
import com.bitplay.api.domain.OrderBookJson;
import com.bitplay.api.domain.OrderJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.api.service.BitplayUIServiceBitmex;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@RestController
@RequestMapping("/market/bitmex")
public class BitmexEndpoint {

    @Autowired
    @Qualifier("Bitmex")
    private BitplayUIServiceBitmex bitmex;

    @RequestMapping(value = "/order-book", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderBookJson okCoinOrderBook() {
        return this.bitmex.getOrderBook();
    }

    @RequestMapping(value = "/account", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountInfoJson getAccountInfo() {
        return this.bitmex.getContractsAccountInfo();
    }

    @RequestMapping(value = "/place-market-order",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeResponseJson placeMarketOrder(TradeRequestJson tradeRequestJson) {
        return this.bitmex.doTrade(tradeRequestJson);
    }

    @RequestMapping(value = "/trade-history", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VisualTrade> tradeHistory() {
        return this.bitmex.fetchTrades();
    }

    @RequestMapping(value = "/open-orders", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrderJson> openOrders() {
        return this.bitmex.getOpenOrders();
    }

    @RequestMapping(value = "/open-orders/move",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson openOrders(OrderJson orderJson) {
        return this.bitmex.moveOpenOrder(orderJson);
    }

    @RequestMapping(value = "/future-index", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public FutureIndexJson futureIndex() {
        return this.bitmex.getFutureIndex();
    }

    @RequestMapping(value = "/custom-swap-time",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson futureIndex(ChangeRequestJson changeRequestJson) {
        return this.bitmex.setCustomSwapTime(changeRequestJson);
    }

    @RequestMapping(value = "/liq-info", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public LiquidationInfoJson getLiquidationInfo() {
        return this.bitmex.getLiquidationInfoJson();
    }

    @RequestMapping(value = "/liq-info",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LiquidationInfoJson resetLiquidationInfo(ChangeRequestJson json) {
        return this.bitmex.resetLiquidationInfoJson();
    }
}

