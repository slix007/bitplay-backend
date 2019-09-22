package com.bitplay.api.controller;

import com.bitplay.api.dto.AccountInfoJson;
import com.bitplay.api.dto.ChangeRequestJson;
import com.bitplay.api.dto.LiquidationInfoJson;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.TradeRequestJson;
import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.api.dto.ob.OrderBookJson;
import com.bitplay.api.dto.ob.OrderJson;
import com.bitplay.api.service.BitplayUIServiceBitmex;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Secured("ROLE_TRADER")
@RestController
@RequestMapping("/market/bitmex")
public class BitmexEndpoint {

    @Autowired
    @Qualifier("Bitmex")
    private BitplayUIServiceBitmex bitmex;

    @RequestMapping(value = "/order-book", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderBookJson getOrderBook() {
        try {
            return this.bitmex.getOrderBook();
        } catch (NotYetInitializedException e) {
            return new OrderBookJson();
        }
    }

    @RequestMapping(value = "/account", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountInfoJson getAccountInfo() {
        return this.bitmex.getFullAccountInfo();
    }

    @RequestMapping(value = "/place-market-order",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public TradeResponseJson placeMarketOrder(@RequestBody TradeRequestJson tradeRequestJson) {
        return this.bitmex.doTrade(tradeRequestJson);
    }

    @RequestMapping(value = "/open-orders", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrderJson> openOrders() {
        return this.bitmex.getOpenOrders();
    }

    @RequestMapping(value = "/open-orders/move",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson openOrders(@RequestBody OrderJson orderJson) {
        return this.bitmex.moveOpenOrder(orderJson);
    }

    @RequestMapping(value = "/custom-swap-time",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson setCustomSwapTime(@RequestBody ChangeRequestJson changeRequestJson) {
        return this.bitmex.setCustomSwapTime(changeRequestJson);
    }

    @RequestMapping(value = "/reset-time-compare",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson resetTimeCompare() {
        return this.bitmex.resetTimeCompare();
    }

    @RequestMapping(value = "/update-time-compare-updating",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson updateTimeCompareUpdating(@RequestBody ChangeRequestJson changeRequestJson) {
        return this.bitmex.updateTimeCompareUpdating(changeRequestJson);
    }

    @RequestMapping(value = "/liq-info", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public LiquidationInfoJson getLiquidationInfo() {
        return this.bitmex.getLiquidationInfoJson();
    }

    @RequestMapping(value = "/liq-info",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public LiquidationInfoJson resetLiquidationInfo() {
        return this.bitmex.resetLiquidationInfoJson();
    }

    @RequestMapping(value = "/open-orders/cancel",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson openOrdersCancel(@RequestBody OrderJson orderJson) {
        final String id = orderJson.getId();
//        boolean cancelFromUI = this.bitmex.getBusinessService().cancelAllOrders("CancelFromUI");
        boolean cancelFromUI = this.bitmex.getBusinessService().cancelOrderSync(id, "CancelFromUI");
        return new ResultJson(String.valueOf(cancelFromUI), "");
    }

    @RequestMapping(value = "/open-orders/cancel-all",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson openOrdersCancelAll() {
        final Integer qty = this.bitmex.getBusinessService().cancelAllPortions();
        return new ResultJson(qty + "_cancelled", "");
    }

    @RequestMapping(value = "/close-all-pos",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public TradeResponseJson closeAllPos() {
        return this.bitmex.closeAllPos();
    }

}

