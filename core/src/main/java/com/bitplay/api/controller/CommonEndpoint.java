package com.bitplay.api.controller;

import com.bitplay.TwoMarketStarter;
import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.DeltasMinMaxJson;
import com.bitplay.api.domain.LiqParamsJson;
import com.bitplay.api.domain.MarketFlagsJson;
import com.bitplay.api.domain.MarketList;
import com.bitplay.api.domain.PosCorrJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.SumBalJson;
import com.bitplay.api.domain.TimersJson;
import com.bitplay.api.domain.states.MarketStatesJson;
import com.bitplay.api.service.CommonUIService;
import com.bitplay.market.MarketService;
import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.bitplay.security.TraderPermissionsService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@Secured("ROLE_TRADER")
@RestController
public class CommonEndpoint {

    @Autowired
    private CommonUIService commonUIService;

    @Autowired
    private TwoMarketStarter twoMarketStarter;

    @Autowired
    private TraderPermissionsService traderPermissionsService;

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
        final MarketService first = twoMarketStarter.getFirstMarketService();
        final String firstName = first.getName();
        final String firstFuturesContractName = first.getFuturesContractName();
        final MarketService second = twoMarketStarter.getSecondMarketService();
        final String secondName = second.getName();
        final String secondFuturesContract = second.getFuturesContractName();
        return new MarketList(firstName, secondName, firstFuturesContractName, secondFuturesContract);
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

    @RequestMapping(value = "/market/cum-params", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CumParams> cumParamsList() {
        return commonUIService.getCumParamsList();
    }

    @RequestMapping(value = "/market/cum-params/reset",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public List<CumParams> resetCumParams(@RequestBody CumParams cumParams) {
        return commonUIService.resetCumParams(cumParams);
    }

    @RequestMapping(value = "/market/cum-params/update",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public List<CumParams> updateCumParams(@RequestBody CumParams cumParams) {
        return commonUIService.updateCumParams(cumParams);
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
    public MarketFlagsJson freeStates() {
        // forbidden when eBestMinViolates, but ok with market states FORBIDDEN
        if (!traderPermissionsService.isEBestMinOk()) {
            return new MarketFlagsJson(false, false);
        }
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

//    @RequestMapping(value = "/market/pos-diff", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
//    public PosDiffJson getPositionEquality() {
//        return commonUIService.getPosDiff();
//    }

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

    @RequestMapping(value = "/reset-delta-params",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public DeltasMinMaxJson resetDeltaParams() {
        return commonUIService.resetDeltaParamsJson();
    }

    @RequestMapping(value = "/reset-delta-params-min",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public DeltasMinMaxJson resetDeltaMinParams() {
        return commonUIService.resetDeltaMinParamsJson();
    }

    @RequestMapping(value = "/reset-signal-time-params",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public DeltasMinMaxJson resetSignalTimeParams() {
        return commonUIService.resetSignalTimeParams();
    }

    @RequestMapping(value = "/restart-monitoring-params",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public DeltasMinMaxJson getRestartMonitoringParams() {
        return commonUIService.getRestartMonitoringParamsJson();
    }

    @RequestMapping(value = "/restart-monitoring-params",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public DeltasMinMaxJson resetRestartMonitoringParams() {
        return commonUIService.resetRestartMonitoringParamsJson();
    }
    @RequestMapping(value = "/market/timers",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TimersJson timers() {
        return commonUIService.getTimersJson();
    }

    @RequestMapping(value = "/market/last-price-deviation",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LastPriceDeviation getLastPriceDeviation() {
        return commonUIService.getLastPriceDeviation();
    }

    @RequestMapping(value = "/market/last-price-deviation/fix",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public LastPriceDeviation fixLastPriceDeviation() {
        return commonUIService.fixLastPriceDeviation();
    }

    @RequestMapping(value = "/market/last-price-deviation",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public LastPriceDeviation updateLastPriceDeviation(@RequestBody LastPriceDeviation lastPriceDeviation) {
        return commonUIService.updateLastPriceDeviation(lastPriceDeviation);
    }

}
