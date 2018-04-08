package com.bitplay.api.controller;

import com.bitplay.api.domain.AccountInfoJson;
import com.bitplay.api.domain.futureindex.FutureIndexJson;
import com.bitplay.api.domain.LiquidationInfoJson;
import com.bitplay.api.domain.OrderBookJson;
import com.bitplay.api.domain.OrderJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.api.service.BitplayUIServiceOkCoin;

import org.knowm.xchange.okcoin.dto.trade.OkCoinTradeResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@RestController
@RequestMapping("/market/okcoin")
public class OkCoinEndpoint {

    @Autowired
    @Qualifier("OkCoin")
    private BitplayUIServiceOkCoin okCoin;

    @RequestMapping(value = "/order-book", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderBookJson okCoinOrderBook() {
        return this.okCoin.getOrderBook();
    }

    @RequestMapping(value = "/account", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountInfoJson getAccountInfo() {
        return this.okCoin.getFullAccountInfo();
    }

    @RequestMapping(value = "/place-market-order",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeResponseJson placeMarketOrder(@RequestBody TradeRequestJson tradeRequestJson) {
        return this.okCoin.doTrade(tradeRequestJson);
    }

    @RequestMapping(value = "/trade-history", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VisualTrade> tradeHistory() {
        return this.okCoin.fetchTrades();
    }

    @RequestMapping(value = "/open-orders", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrderJson> openOrders() {
        return this.okCoin.getOpenOrders();
    }

    @RequestMapping(value = "/open-orders/move",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson openOrdersMove(@RequestBody OrderJson orderJson) {
        return this.okCoin.moveOpenOrder(orderJson);
    }

    @RequestMapping(value = "/open-orders/cancel",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson openOrdersCancel(@RequestBody OrderJson orderJson) {
        final String id = orderJson.getId();
        final OkCoinTradeResult cancelResult = this.okCoin.getBusinessService().cancelOrderSync(id, "CancelFromUI");
        return new ResultJson(String.valueOf(cancelResult.isResult()), cancelResult.getDetails());
    }

    @RequestMapping(value = "/future-index", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public FutureIndexJson futureIndex() {
        return this.okCoin.getFutureIndex();
    }

    @RequestMapping(value = "/liq-info", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public LiquidationInfoJson getLiquidationInfo() {
        return this.okCoin.getLiquidationInfoJson();
    }

    @RequestMapping(value = "/liq-info",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LiquidationInfoJson resetLiquidationInfo() {
        return this.okCoin.resetLiquidationInfoJson();
    }

}

