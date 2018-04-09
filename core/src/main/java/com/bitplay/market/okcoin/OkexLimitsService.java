package com.bitplay.market.okcoin;

import com.bitplay.api.domain.futureindex.LimitsJson;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.Limits;

import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/8/18.
 */
@Service
public class OkexLimitsService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private volatile boolean insideLimitsSavedStatus = true;

    public LimitsJson getLimitsJson() {
        final Ticker ticker = okCoinService.getTicker();
        final BigDecimal minPrice = ticker.getLow();
        final BigDecimal maxPrice = ticker.getHigh();

        final Limits limits = settingsRepositoryService.getSettings().getLimits();
        final BigDecimal okexLimitPrice = limits.getOkexLimitPrice();
        final int ind = okexLimitPrice.intValue() - 1;
        final OrderBook ob = okCoinService.getOrderBook();
        final BigDecimal limitBid = ob.getBids().get(ind).getLimitPrice();
        final BigDecimal limitAsk = ob.getAsks().get(ind).getLimitPrice();

        // insideLimits: Limit Ask < Max price && Limit bid > Min price
        final boolean insideLimits = (limitAsk.compareTo(maxPrice) < 0 && limitBid.compareTo(minPrice) > 0);

        if (insideLimitsSavedStatus != insideLimits) {
            insideLimitsSavedStatus = insideLimits;
            String status = insideLimits ? "Inside limits" : "Outside limits";
            warningLogger.warn("Change okex limits to " + status);
        }

        return new LimitsJson(okexLimitPrice, limitAsk, limitBid, minPrice, maxPrice, insideLimits, limits.getIgnoreLimits());
    }

    public boolean outsideLimits() {
        final LimitsJson limits = getLimitsJson();
        final Boolean doCheck = !limits.getIgnoreLimits();
        final Boolean outsideLimits = !limits.getInsideLimits();
        return doCheck && outsideLimits;
    }

}
