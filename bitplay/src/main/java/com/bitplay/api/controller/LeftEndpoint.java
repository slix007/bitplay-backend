package com.bitplay.api.controller;

import com.bitplay.api.dto.AccountInfoJson;
import com.bitplay.api.dto.ChangeRequestJson;
import com.bitplay.api.dto.LiquidationInfoJson;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.TradeRequestJson;
import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.api.dto.ob.OrderBookJson;
import com.bitplay.api.dto.ob.OrderJson;
import com.bitplay.api.service.LeftUiService;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.model.ex.OrderResultTiny;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/market/left")
@RequiredArgsConstructor
public class LeftEndpoint {

    private final LeftUiService leftUiService;

    @RequestMapping(value = "/order-book", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderBookJson getOrderBook() {
        try {
            return this.leftUiService.getOrderBook();
        } catch (NotYetInitializedException e) {
            return new OrderBookJson();
        }
    }

    @RequestMapping(value = "/account", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountInfoJson getAccountInfo() {
        return this.leftUiService.getFullAccountInfo();
    }

    @RequestMapping(value = "/place-market-order",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public TradeResponseJson placeMarketOrder(@RequestBody TradeRequestJson tradeRequestJson) {
        return this.leftUiService.doTrade(tradeRequestJson);
    }

    @RequestMapping(value = "/open-orders", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrderJson> openOrders() {
        return this.leftUiService.getOpenOrders();
    }

    @RequestMapping(value = "/open-orders/move",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson openOrders(@RequestBody OrderJson orderJson) {
        return this.leftUiService.moveOpenOrder(orderJson);
    }

    @RequestMapping(value = "/custom-swap-time",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson setCustomSwapTime(@RequestBody ChangeRequestJson changeRequestJson) {
        return this.leftUiService.setCustomSwapTime(changeRequestJson);
    }

    @RequestMapping(value = "/reset-time-compare",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson resetTimeCompare() {
        return this.leftUiService.resetTimeCompare();
    }

    @RequestMapping(value = "/update-time-compare-updating",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson updateTimeCompareUpdating(@RequestBody ChangeRequestJson changeRequestJson) {
        return this.leftUiService.updateTimeCompareUpdating(changeRequestJson);
    }

    @RequestMapping(value = "/liq-info", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public LiquidationInfoJson getLiquidationInfo() {
        return this.leftUiService.getLiquidationInfoJson();
    }

    @RequestMapping(value = "/liq-info",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public LiquidationInfoJson resetLiquidationInfo() {
        return this.leftUiService.resetLiquidationInfoJson();
    }

    @RequestMapping(value = "/open-orders/cancel",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson openOrdersCancel(@RequestBody OrderJson orderJson) {
        final String id = orderJson.getId();
//        boolean cancelFromUI = this.bitmex.getBusinessService().cancelAllOrders("CancelFromUI");
        final OrderResultTiny cancelResult = this.leftUiService.getBusinessService().cancelOrderSync(id, "CancelFromUI");
        return new ResultJson(String.valueOf(cancelResult.isResult()), cancelResult.getError_message());

    }

    @RequestMapping(value = "/open-orders/cancel-all",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson openOrdersCancelAll() {
        final Integer qty = this.leftUiService.getBusinessService().cancelAllPortions();
        return new ResultJson(qty + "_cancelled", "");
    }

    @RequestMapping(value = "/close-all-pos",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public TradeResponseJson closeAllPos() {
        return this.leftUiService.closeAllPos();
    }

}

