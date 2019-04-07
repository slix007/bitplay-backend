package com.bitplay.api.controller;

import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.TradeLogJson;
import com.bitplay.api.service.CommonUIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@RestController
public class LogsEndpoint {

    @Autowired
    private CommonUIService commonUIService;

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


}
