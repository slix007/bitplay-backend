package com.bitplay.api.controller;

import com.bitplay.TwoMarketStarter;
import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.DeltasMinMaxJson;
import com.bitplay.api.domain.LiqParamsJson;
import com.bitplay.api.domain.MarketFlagsJson;
import com.bitplay.api.domain.MarketList;
import com.bitplay.api.domain.MarketStatesJson;
import com.bitplay.api.domain.PosCorrJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.SumBalJson;
import com.bitplay.api.domain.TradeLogJson;
import com.bitplay.api.service.CommonUIService;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@RestController
public class CommonEndpoint {

    //TODO move all relevant methods to SettingsEndpoint

    @Autowired
    private CommonUIService commonUIService;

    @Autowired
    private TwoMarketStarter twoMarketStarter;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String help() {
        return "Use /market/{marketName}";
    }

    @RequestMapping(value = "/market", method = RequestMethod.GET)
    public String message() {
        return "Hello. Use /market/{marketName}/{operationName}";
    }

    @RequestMapping(value = "/market/list", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public MarketList getMarkets() {
        final String first = twoMarketStarter.getFirstMarketService().getName();
        final String second = twoMarketStarter.getSecondMarketService().getName();
        return new MarketList(first, second);
    }

    @RequestMapping(value = "/market/trade-log/poloniex", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeLogJson getPoloniexTradeLog() {
        return commonUIService.getPoloniexTradeLog();
    }

    @RequestMapping(value = "/market/trade-log/bitmex", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeLogJson getBitmexTradeLog(@RequestParam(value = "date", required = false) String date) {
        return commonUIService.getTradeLog("bitmex", date);
    }

    @RequestMapping(value = "/market/trade-log/okcoin", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeLogJson getOkcoinTradeLog(@RequestParam(value = "date",required = false) String date) {
        return commonUIService.getTradeLog("okcoin", date);
    }

    @RequestMapping(value = "/market/deltas-log", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeLogJson getDeltasLog(@RequestParam(value = "date", required = false) String date) {
        return commonUIService.getDeltasLog(date);
    }

    @RequestMapping(value = "/market/warning-log", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeLogJson getWarningLog(@RequestParam(value = "date", required = false) String date) {
        return commonUIService.getWarningLog(date);
    }

    @RequestMapping(value = "/market/debug-log", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeLogJson getDebugLog(@RequestParam(value = "date", required = false) String date) {
        return commonUIService.getDebugLog(date);
    }

    @RequestMapping(value = "/market/deltas", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public DeltasJson deltas() {
        return commonUIService.getDeltas();
    }

    @RequestMapping(value = "/market/update-borders",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public DeltasJson updateBorders(@RequestBody BorderUpdateJson borderUpdateJson) {
        return commonUIService.updateBorders(borderUpdateJson);
    }

    @RequestMapping(value = "/market/update-maker-delta",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public DeltasJson updateMakerDelta(@RequestBody DeltalUpdateJson deltalUpdateJson) {
        return commonUIService.updateMakerDelta(deltalUpdateJson);
    }

    @RequestMapping(value = "/market/stop-moving", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public MarketFlagsJson getMovingStop() {
        return commonUIService.getStopMoving();
    }

    @RequestMapping(value = "/market/toggle-stop-moving",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public MarketFlagsJson updateMovingStop() {
        return commonUIService.toggleStopMoving();
    }

    @RequestMapping(value = "/market/states", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public MarketStatesJson getMarketsState() {
        return commonUIService.getMarketsStates();
    }

    @RequestMapping(value = "/market/states", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public MarketStatesJson setMarketsState(@RequestBody MarketStatesJson marketStatesJson) {
        return commonUIService.setMarketsStates(marketStatesJson);
    }

    @RequestMapping(value = "/market/free-states",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public MarketFlagsJson freeStates() {
        return commonUIService.freeMarketsStates();
    }

//    @RequestMapping(value = "/market/tradable-amount", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
//    public TradableAmountJson getTradableAmount() {
//        return commonUIService.getTradableAmount();
//    }

//    @RequestMapping(value = "/market/tradable-amount",
//            method = RequestMethod.POST,
//            consumes = MediaType.APPLICATION_JSON_VALUE,
//            produces = MediaType.APPLICATION_JSON_VALUE)
//    public TradableAmountJson setTradableAmount(@RequestBody TradableAmountJson tradableAmountJson) {
//        return commonUIService.updateTradableAmount(tradableAmountJson);
//    }

    @RequestMapping(value = "/market/print-sum-bal",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson printSumBal() {
        return commonUIService.printSumBal();
    }

    @RequestMapping(value = "/market/sum-bal", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public SumBalJson getSumBal() {
        return commonUIService.getSumBal();
    }

    @RequestMapping(value = "/market/pos-diff", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson getPositionEquality() {
        ResultJson posDiff;
        try {
            posDiff = commonUIService.getPosDiff();
        } catch (NotYetInitializedException e) {
            // do nothing
            posDiff = new ResultJson("NotInitialized", "position is not yet initialized");
        }
        return posDiff;
    }

    @RequestMapping(value = "/market/pos-corr",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public PosCorrJson updatePosCorr(@RequestBody PosCorrJson posCorrJson) {
        return commonUIService.updatePosCorr(posCorrJson);
    }

    @RequestMapping(value = "/market/pos-corr",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PosCorrJson getPosCorr() {
        return commonUIService.getPosCorr();
    }

    @RequestMapping(value = "/market/liq-params",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LiqParamsJson getLiqParams() {
        return commonUIService.getLiqParams();
    }

    @RequestMapping(value = "/market/liq-params",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public LiqParamsJson updateLiqParams(@RequestBody LiqParamsJson liqParamsJson) {
        return commonUIService.updateLiqParams(liqParamsJson);
    }

    @RequestMapping(value = "/delta-params",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public DeltasMinMaxJson getDeltaParams() {
        return commonUIService.getDeltaParamsJson();
    }

    @RequestMapping(value = "/delta-params",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public DeltasMinMaxJson resetDeltaParams() {
        return commonUIService.resetDeltaParamsJson();
    }

    @RequestMapping(value = "/restart-monitoring-params",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public DeltasMinMaxJson getRestartMonitoringParams() {
        return commonUIService.geRestartMonitoringParamsJson();
    }

    @RequestMapping(value = "/restart-monitoring-params",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public DeltasMinMaxJson resetRestartMonitoringParams() {
        return commonUIService.resetRestartMonitoringParamsJson();
    }


    @RequestMapping(value = "/market/borders-timer",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson bordersTimer() {
        return commonUIService.getUpdateBordersTimerString();
    }

}
