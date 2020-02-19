package com.bitplay.market.bitmex;

import com.bitplay.api.dto.ob.LimitsJson;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.LimitsService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.Limits;
import com.bitplay.utils.Utils;
import info.bitrich.xchangestream.bitmex.dto.BitmexContractIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by Sergey Shurmin on 4/8/18.
 */
@Slf4j
@RequiredArgsConstructor
public class BitmexLimitsService implements LimitsService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final SlackNotifications slackNotifications;
    private final BitmexService bitmexService;
    private final SettingsRepositoryService settingsRepositoryService;

    private volatile boolean insideLimitsSavedStatus = true;

    @Override
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
        final BigDecimal limitBid = ob.getBids().size() > 0 ? ob.getBids().get(0).getLimitPrice() : BigDecimal.ZERO;
        final BigDecimal limitAsk = ob.getAsks().size() > 0 ? ob.getAsks().get(0).getLimitPrice() : BigDecimal.ZERO;
//        Max price = Index (1 + Limit price, %)
//        Min price = Index (1 - Limit price, %)
        final BigDecimal lp = bitmexLimitPrice.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        final BigDecimal maxPrice = indexPrice.multiply(BigDecimal.ONE.add(lp)).setScale(2, RoundingMode.HALF_UP);
        final BigDecimal minPrice = indexPrice.multiply(BigDecimal.ONE.subtract(lp)).setScale(2, RoundingMode.HALF_UP);
        final String priceRangeTimestamp = Utils.timestampToStr(contractIndex.getTimestamp());
        // insideLimits: Limit Ask < Max price && Limit bid > Min price
        final boolean insideLimits = (limitAsk.compareTo(maxPrice) < 0 && limitBid.compareTo(minPrice) > 0);

        if (insideLimitsSavedStatus != insideLimits) {
            insideLimitsSavedStatus = insideLimits;
            final String status = insideLimits ? "Inside limits" : "Outside limits";
            final String limitsStr = String.format("Limit ask / Max price = %s / %s ; Limit bid / Min price = %s / %s",
                    limitAsk.toPlainString(),
                    maxPrice.toPlainString(),
                    limitBid.toPlainString(),
                    minPrice.toPlainString());
            final String msg = String.format("Change bitmex limits to %s. %s", status, limitsStr);
            warningLogger.warn(msg);
            log.info(msg);
        }

        return new LimitsJson(
                bitmexLimitPrice,
                limitAsk,
                limitBid,
                minPrice,
                maxPrice,
                priceRangeTimestamp,
                insideLimits,
                null,
                limits.getIgnoreLimits());
    }

    @Override
    public boolean outsideLimits() {
        final LimitsJson limits = getLimitsJson();
        final Boolean doCheck = !limits.getIgnoreLimits();
        final Boolean outsideLimits = !limits.getInsideLimits();
        if (outsideLimits && !bitmexService.isReconnectInProgress()) {
            slackNotifications.sendNotify(NotifyType.BITMEX_OUTSIDE_LIMITS, BitmexService.NAME + " outsideLimits");
        }

        return doCheck && outsideLimits;
    }

    @Override
    public boolean outsideLimitsForPreliq(BigDecimal currentPos) {
        return outsideLimits();
    }
}
