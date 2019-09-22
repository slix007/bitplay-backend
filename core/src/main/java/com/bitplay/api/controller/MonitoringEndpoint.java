package com.bitplay.api.controller;

import com.bitplay.api.dto.MonDeltaListJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.DeltasCalcService;
import com.bitplay.arbitrage.dto.DeltaMon;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mon")
public class MonitoringEndpoint {

    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private DeltasCalcService deltasCalcService;

    @RequestMapping(value = "/calc-delta", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public DeltaMon getDeltaMon() {
        return arbitrageService.getDeltaMon();
    }

    @RequestMapping(value = "/calc-delta/reset", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public DeltaMon getDeltaMonReset() {
        arbitrageService.setDeltaMon(new DeltaMon());
        return arbitrageService.getDeltaMon();
    }

    @RequestMapping(value = "/calc-delta/list", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public MonDeltaListJson getDeltaListForCalc() {
        return new MonDeltaListJson(
                deltasCalcService.getCurrBtmDeltasInCalc(),
                deltasCalcService.getCurrOkDeltasInCalc()
        );
    }

    @RequestMapping(value = "/calc-delta/list/bitmex", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<Instant, BigDecimal> getBtmDeltaListForCalc() {
        return deltasCalcService.getCurrBtmDeltasInCalc();
    }

    @RequestMapping(value = "/calc-delta/list/okex", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<Instant, BigDecimal> getOkDeltaListForCalc() {
        return deltasCalcService.getCurrOkDeltasInCalc();
    }

    @RequestMapping(value = "/calc-delta/list/bitmex-all", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<Instant, BigDecimal> getBtmDeltaList() {
        return deltasCalcService.getCurrBtmDeltas();
    }

    @RequestMapping(value = "/calc-delta/list/okex-all", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<Instant, BigDecimal> getOkDeltaList() {
        return deltasCalcService.getCurrOkDeltas();
    }
}
