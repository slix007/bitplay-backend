package com.bitplay.api.controller;

import com.bitplay.api.domain.MonDeltaListJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.AvgDeltaInMemory;
import com.bitplay.arbitrage.DeltasCalcService;
import com.bitplay.arbitrage.dto.DeltaMon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mon")
public class MonitoringEndpoint {

    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private AvgDeltaInMemory avgDeltaInMemory;

    @RequestMapping(value = "/calc-delta", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public DeltaMon getDeltaMon() {
        return arbitrageService.getDeltaMon();
    }

    @RequestMapping(value = "/calc-delta/reset", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public DeltaMon getDeltaMonReset() {
        arbitrageService.setDeltaMon(new DeltaMon());
        return arbitrageService.getDeltaMon();
    }

    @RequestMapping(value = "/calc-delta/list", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public MonDeltaListJson getDeltaListForCalc() {
        return new MonDeltaListJson(
                avgDeltaInMemory.getBDeltaCache().asMap(),
                avgDeltaInMemory.getODeltaCache().asMap()
        );
    }

}
