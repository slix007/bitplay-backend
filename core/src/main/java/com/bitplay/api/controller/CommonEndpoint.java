package com.bitplay.api.controller;

import com.bitplay.TwoMarketStarter;
import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.MarketList;
import com.bitplay.api.domain.StopMovingJson;
import com.bitplay.api.domain.TradableAmountJson;
import com.bitplay.api.domain.TradeLogJson;
import com.bitplay.api.service.CommonUIService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@Component
@Path("/")
public class CommonEndpoint {

    @Autowired
    private CommonUIService commonUIService;

    @Autowired
    private TwoMarketStarter twoMarketStarter;

    @GET
    @Path("/")
    public String help() {
        return "Use /market/{marketName}";
    }

    @GET
    @Path("/market")
    public String message() {
        return "Hello. Use /market/{marketName}/{operationName}";
    }

    @GET
    @Path("/market/list")
    @Produces("application/json")
    public MarketList getMarkets() {
        final String first = twoMarketStarter.getFirstMarketService().getName();
        final String second = twoMarketStarter.getSecondMarketService().getName();
        return new MarketList(first, second);
    }

    @GET
    @Path("/market/trade-log/poloniex")
    @Produces(MediaType.APPLICATION_JSON)
    public TradeLogJson getPoloniexTradeLog() {
        return commonUIService.getPoloniexTradeLog();
    }

    @GET
    @Path("/market/trade-log/bitmex")
    @Produces(MediaType.APPLICATION_JSON)
    public TradeLogJson getBitmexTradeLog() {
        return commonUIService.getBitmexTradeLog();
    }

    @GET
    @Path("/market/trade-log/okcoin")
    @Produces(MediaType.APPLICATION_JSON)
    public TradeLogJson getOkcoinTradeLog() {
        return commonUIService.getOkCoinTradeLog();
    }

    @GET
    @Path("/market/deltas-log")
    @Produces(MediaType.APPLICATION_JSON)
    public TradeLogJson getDeltasLog() {
        return commonUIService.getDeltasLog();
    }

    @GET
    @Path("/market/deltas")
    @Produces(MediaType.APPLICATION_JSON)
    public DeltasJson deltas() {
        return commonUIService.getDeltas();
    }

    @POST
    @Path("/market/update-borders")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public DeltasJson updateBorders(BorderUpdateJson borderUpdateJson) {
        return commonUIService.updateBorders(borderUpdateJson);
    }

    @POST
    @Path("/market/update-maker-delta")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public DeltasJson updateMakerDelta(DeltalUpdateJson deltalUpdateJson) {
        return commonUIService.updateMakerDelta(deltalUpdateJson);
    }

    @GET
    @Path("/market/stop-moving")
    @Produces(MediaType.APPLICATION_JSON)
    public StopMovingJson getMovingStop() {
        return commonUIService.getStopMoving();
    }

    @POST
    @Path("/market/toggle-stop-moving")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public StopMovingJson updateMovingStop() {
        return commonUIService.toggleStopMoving();
    }

    @GET
    @Path("/market/tradable-amount")
    @Produces(MediaType.APPLICATION_JSON)
    public TradableAmountJson getTradableAmount() {
        return commonUIService.getTradableAmount();
    }

    @POST
    @Path("/market/tradable-amount")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TradableAmountJson setTradableAmount(TradableAmountJson tradableAmountJson) {
        return commonUIService.updateTradableAmount(tradableAmountJson);
    }
}
