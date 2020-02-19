package com.bitplay.api.controller;

import com.bitplay.api.dto.AccountInfoJson;
import com.bitplay.api.dto.LeverageRequest;
import com.bitplay.api.dto.LiquidationInfoJson;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.TradeRequestJson;
import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.api.dto.ob.OrderBookJson;
import com.bitplay.api.dto.ob.OrderJson;
import com.bitplay.api.service.BitplayUIServiceOkCoin;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.model.ex.OrderResultTiny;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Secured("ROLE_TRADER")
@RestController
@RequestMapping("/market/okcoin")
public class OkCoinEndpoint {

    @Autowired
    @Qualifier("OkCoin")
    private BitplayUIServiceOkCoin okCoin;

    @Autowired
    private ArbitrageService arbitrageService;

    @RequestMapping(value = "/raw-account", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountInfoContracts rawAccount() {
        return ((OkCoinService) arbitrageService.getRightMarketService()).getAccountApiV3();
    }

    @RequestMapping(value = "/order-book", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderBookJson okCoinOrderBook() {
        try {
            return this.okCoin.getOrderBook();
        } catch (NotYetInitializedException e) {
            return new OrderBookJson();
        }
    }

    @RequestMapping(value = "/account", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountInfoJson getAccountInfo() {
        return this.okCoin.getFullAccountInfo();
    }

    @RequestMapping(value = "/place-market-order",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public TradeResponseJson placeMarketOrder(@RequestBody TradeRequestJson tradeRequestJson) {
        return this.okCoin.doTrade(tradeRequestJson);
    }

    @RequestMapping(value = "/open-orders", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrderJson> openOrders() {
        return this.okCoin.getOpenOrders();
    }

    @RequestMapping(value = "/open-orders/move",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson openOrdersMove(@RequestBody OrderJson orderJson) {
        return this.okCoin.moveOpenOrder(orderJson);
    }

    @RequestMapping(value = "/open-orders/cancel",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson openOrdersCancel(@RequestBody OrderJson orderJson) {
        final String id = orderJson.getId();
        final OrderResultTiny cancelResult = this.okCoin.getBusinessService().cancelOrderSync(id, "CancelFromUI");
        return new ResultJson(String.valueOf(cancelResult.isResult()), cancelResult.getError_message());
    }

    @RequestMapping(value = "/open-orders/cancel-all",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson openOrdersCancelAll() {
//        final String id = orderJson.getId();
//        final OkCoinTradeResult cancelResult = this.okCoin.getBusinessService().cancelOrderSyncFromUi(id, "CancelFromUI");
        final Integer qty = this.okCoin.getBusinessService().cancelAllPortions();
        return new ResultJson(qty + "_cancelled", "");
    }

    @RequestMapping(value = "/liq-info", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public LiquidationInfoJson getLiquidationInfo() {
        return this.okCoin.getLiquidationInfoJson();
    }

    @RequestMapping(value = "/liq-info",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public LiquidationInfoJson resetLiquidationInfo() {
        return this.okCoin.resetLiquidationInfoJson();
    }

    @RequestMapping(value = "/close-all-pos",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public TradeResponseJson closeAllPos() {
        return this.okCoin.closeAllPos();
    }

    @RequestMapping(value = "/change-leverage",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson changeLeverage(@RequestBody LeverageRequest leverageRequest) {
        return this.okCoin.changeLeverage(leverageRequest);
    }

}

