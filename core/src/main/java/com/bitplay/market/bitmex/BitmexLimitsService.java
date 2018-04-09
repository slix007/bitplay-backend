package com.bitplay.market.bitmex;

import com.bitplay.api.domain.futureindex.LimitsJson;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.Limits;

import info.bitrich.xchangestream.bitmex.dto.BitmexContractIndex;

import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by Sergey Shurmin on 4/8/18.
 */
@Service
public class BitmexLimitsService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private volatile boolean insideLimitsSavedStatus = true;

    public LimitsJson getLimitsJson() {
        final BitmexContractIndex contractIndex;
        try {
            contractIndex = (BitmexContractIndex) bitmexService.getContractIndex();
        } catch (ClassCastException e) {
            throw new NotYetInitializedException();
        }
        final BigDecimal indexPrice = contractIndex.getIndexPrice();

        final Limits limits = settingsRepositoryService.getSettings().getLimits();
        final BigDecimal bitmexLimitPrice = limits.getBitmexLimitPrice();
        final OrderBook ob = bitmexService.getOrderBook();
        final BigDecimal limitBid = ob.getBids().get(0).getLimitPrice();
        final BigDecimal limitAsk = ob.getAsks().get(0).getLimitPrice();
//        Max price = Index (1 + Limit price, %)
//        Min price = Index (1 - Limit price, %)
        final BigDecimal lp = bitmexLimitPrice.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        final BigDecimal maxPrice = indexPrice.multiply(BigDecimal.ONE.add(lp)).setScale(2, RoundingMode.HALF_UP);
        final BigDecimal minPrice = indexPrice.multiply(BigDecimal.ONE.subtract(lp)).setScale(2, RoundingMode.HALF_UP);
        // insideLimits: Limit Ask < Max price && Limit bid > Min price
        final boolean insideLimits = (limitAsk.compareTo(maxPrice) < 0 && limitBid.compareTo(minPrice) > 0);

        if (insideLimitsSavedStatus != insideLimits) {
            insideLimitsSavedStatus = insideLimits;
            String status = insideLimits ? "Inside limits" : "Outside limits";
            warningLogger.warn("Change bitmex limits to " + status);
        }

        return new LimitsJson(
                bitmexLimitPrice,
                limitAsk,
                limitBid,
                minPrice,
                maxPrice,
                insideLimits,
                limits.getIgnoreLimits());
    }

    public boolean outsideLimits() {
        final LimitsJson limits = getLimitsJson();
        final Boolean doCheck = !limits.getIgnoreLimits();
        final Boolean outsideLimits = !limits.getInsideLimits();
        return doCheck && outsideLimits;
    }

}
