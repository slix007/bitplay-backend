package com.bitplay.api.controller;

import com.bitplay.TwoMarketStarter;
import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.ChangeRequestJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.DeltasMinMaxJson;
import com.bitplay.api.domain.LiqParamsJson;
import com.bitplay.api.domain.MarketFlagsJson;
import com.bitplay.api.domain.MarketList;
import com.bitplay.api.domain.MarketStatesJson;
import com.bitplay.api.domain.PlacingTypeJson;
import com.bitplay.api.domain.PosCorrJson;
import com.bitplay.api.domain.ResultJson;
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
import javax.ws.rs.QueryParam;
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
    public TradeLogJson getBitmexTradeLog(@QueryParam("date") String date) {
        return commonUIService.getTradeLog("bitmex", date);
    }

    @GET
    @Path("/market/trade-log/okcoin")
    @Produces(MediaType.APPLICATION_JSON)
    public TradeLogJson getOkcoinTradeLog(@QueryParam("date") String date) {
        return commonUIService.getTradeLog("okcoin", date);
    }

    @GET
    @Path("/market/deltas-log")
    @Produces(MediaType.APPLICATION_JSON)
    public TradeLogJson getDeltasLog(@QueryParam("date") String date) {
        return commonUIService.getDeltasLog(date);
    }

    @GET
    @Path("/market/warning-log")
    @Produces(MediaType.APPLICATION_JSON)
    public TradeLogJson getWarningLog(@QueryParam("date") String date) {
        return commonUIService.getWarningLog(date);
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
    public MarketFlagsJson getMovingStop() {
        return commonUIService.getStopMoving();
    }

    @POST
    @Path("/market/toggle-stop-moving")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public MarketFlagsJson updateMovingStop() {
        return commonUIService.toggleStopMoving();
    }

    @GET
    @Path("/market/states")
    @Produces(MediaType.APPLICATION_JSON)
    public MarketStatesJson getMarketsState() {
        return commonUIService.getMarketsStates();
    }

    @POST
    @Path("/market/free-states")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public MarketFlagsJson freeStates() {
        return commonUIService.freeMarketsStates();
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

    @POST
    @Path("/market/print-sum-bal")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResultJson printSumBal() {
        return commonUIService.printSumBal();
    }

    @GET
    @Path("/market/sum-bal")
    @Produces(MediaType.APPLICATION_JSON)
    public ResultJson getSumBal() {
        return commonUIService.getSumBal();
    }

    @GET
    @Path("/market/pos-diff")
    @Produces(MediaType.APPLICATION_JSON)
    public ResultJson getPositionEquality() {
        return commonUIService.getPosDiff();
    }

    @GET
    @Path("/market/placing-type")
    @Produces(MediaType.APPLICATION_JSON)
    public PlacingTypeJson getPlacingType() {
        return commonUIService.getPlacingType();
    }

    @POST
    @Path("/market/placing-type")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PlacingTypeJson updatePlacingType(PlacingTypeJson placingTypeJson) {
        return commonUIService.updatePlacingType(placingTypeJson);
    }

    @POST
    @Path("/market/pos-corr")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PosCorrJson updatePosCorr(PosCorrJson posCorrJson) {
        return commonUIService.updatePosCorr(posCorrJson);
    }

    @GET
    @Path("/market/pos-corr")
    @Produces(MediaType.APPLICATION_JSON)
    public PosCorrJson getPosCorr() {
        return commonUIService.getPosCorr();
    }

    @GET
    @Path("/market/liq-params")
    @Produces(MediaType.APPLICATION_JSON)
    public LiqParamsJson getLiqParams() {
        return commonUIService.getLiqParams();
    }

    @POST
    @Path("/market/liq-params")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LiqParamsJson updateLiqParams(LiqParamsJson liqParamsJson) {
        return commonUIService.updateLiqParams(liqParamsJson);
    }

    @GET
    @Path("/market/pos-corr-imm")
    @Produces(MediaType.APPLICATION_JSON)
    public ResultJson getImmediateCorrection() {
        return commonUIService.getImmediateCorrection();
    }

    @POST
    @Path("/market/pos-corr-imm")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResultJson updateImmediateCorrection(ChangeRequestJson command) {
        return commonUIService.updateImmediateCorrection(command);
    }

    @GET
    @Path("/delta-params")
    @Produces("application/json")
    public DeltasMinMaxJson getDeltaParams() {
        return commonUIService.getDeltaParamsJson();
    }

    @POST
    @Path("/delta-params")
    @Produces("application/json")
    public DeltasMinMaxJson resetDeltaParams(ChangeRequestJson json) {
        return commonUIService.resetDeltaParamsJson();
    }

    @GET
    @Path("/market/borders-timer")
    @Produces(MediaType.APPLICATION_JSON)
    public ResultJson bordersTimer() {
        return commonUIService.getUpdateBordersTimerString();
    }

}
