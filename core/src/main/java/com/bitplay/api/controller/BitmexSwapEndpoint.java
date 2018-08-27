package com.bitplay.api.controller;

import com.bitplay.api.domain.ResultJson;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.SwapParams;
import com.bitplay.persistance.domain.SwapV2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 10/27/17.
 */
@Secured("ROLE_TRADER")
@RestController
@RequestMapping("/market/bitmex/swap")
public class BitmexSwapEndpoint {
    @Autowired
    PersistenceService persistenceService;

    @Autowired
    BitmexService bitmexService;

    @RequestMapping(value = "", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public SwapParams getSwapParams() {
        return persistenceService.fetchSwapParams("bitmex");
    }

    @RequestMapping(value = "/settings", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson updateBordersSettings(@RequestBody SwapSettings settings) {
        final SwapParams swapParams = persistenceService.fetchSwapParams("bitmex");

        String respDetails = "";
        try {
            if (settings.version != null) {
                swapParams.setActiveVersion(SwapParams.Ver.valueOf(settings.version));
                respDetails = "ver: " + settings.version;
            }
            if (settings.swapV2 != null) {
                if (swapParams.getSwapV2() == null) swapParams.setSwapV2(new SwapV2());

                if (settings.swapV2.getSwapOpenType() != null) {
                    swapParams.getSwapV2().setSwapOpenType(settings.swapV2.getSwapOpenType());
                }
                if (settings.swapV2.getSwapOpenAmount() != null) {
                    swapParams.getSwapV2().setSwapOpenAmount(settings.swapV2.getSwapOpenAmount());
                }
                if (settings.swapV2.getSwapTimeCorrMs() != null) {
                    swapParams.getSwapV2().setSwapTimeCorrMs(settings.swapV2.getSwapTimeCorrMs());

                    if (swapParams.getActiveVersion() == SwapParams.Ver.V2) {
                        bitmexService.getBitmexSwapService().resetTimerToSwapV2Opening(swapParams);
                    }
                }

                respDetails = "swapV2: " + settings.swapV2;
            }
        } catch (Exception e) {
            return new ResultJson("Wrong version", e.getMessage());
        }

        persistenceService.saveSwapParams(swapParams, "bitmex");

        return new ResultJson("OK", respDetails);
    }

    private static class SwapSettings {
        public String version;
        public SwapV2 swapV2;
    }


}
