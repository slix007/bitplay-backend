package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.NtUsdCheckEvent;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.ConBoPortions;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PosDiffPortionsService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private PosDiffService posDiffService;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    /**
     * Runs on each pos change (bitmex on posDiffService event; okex each 200ms).
     */
    @Async("ntUsdSignalCheckExecutor")
    @EventListener(NtUsdCheckEvent.class)
    public void doCheck() {
        if (!arbitrageService.isFirstDeltasCalculated()) {
            return;
        }

        okCoinService.addOoExecutorTask(this::checkAndPlace);
    }

    private void checkAndPlace() {
        final Settings settings = settingsRepositoryService.getSettings();
        if (settings.getArbScheme() != ArbScheme.CON_B_O_PORTIONS) {
            // not portions signal
            return;
        }
        final ConBoPortions conBoPortions = settings.getConBoPortions();
        final BigDecimal minToStart = conBoPortions.getMinNtUsdToStartOkex();
        BigDecimal ntUsd = posDiffService.getDcMainSet();
        if (minToStart.signum() <= 0) {
            // wrong settings
            return;
        }
        if (ntUsd.signum() == 0) {
            // no start
            return;
        }

        if (checkMinToStartNtUsd(ntUsd, minToStart)) {
            return;
        }
        ntUsd = ntUsd.abs();

        if (okCoinService.getMarketState() != MarketState.WAITING_ARB) {
            // wrong state (okex already started?)
            return;
        }

        final BigDecimal maxBlockUsd = conBoPortions.getMaxPortionUsdOkex();
        final BigDecimal usdBlock = ntUsd.compareTo(maxBlockUsd) <= 0
                ? ntUsd
                : maxBlockUsd;
        final BigDecimal block = PlacingBlocks.toOkexCont(usdBlock, settings.isEth());
        final String ntUsdString = String.format("nt_usd(%s)>min_to_start(%s). Okex: maxBlockUsd(%s) ==> usdBlock(%s) => block(%s)",
                ntUsd, minToStart, maxBlockUsd, usdBlock, block);
        if (block.signum() == 0) {
            log.info("WAITING_ARB: PORTIONS: " + ntUsdString);
            okCoinService.getTradeLogger().info("WAITING_ARB: PORTIONS: " + ntUsdString);
            warningLogger.error("WAITING_ARB: PORTIONS: " + ntUsdString);
            resetIfBtmReady();
            return;
        }
        log.info(ntUsdString);

        placeDeferredPortion(block);
    }

    private boolean checkMinToStartNtUsd(BigDecimal ntUsd, BigDecimal minToStart) {
        final PlaceOrderArgs currArgs = okCoinService.getPlaceOrderArgsRef().get();
        final OrderType t = currArgs.getOrderType();
        final boolean b = t == OrderType.BID || t == OrderType.EXIT_ASK;
//        final boolean a = t == OrderType.ASK || t == OrderType.EXIT_BID;
        final BigDecimal ntUsdAbs = b ? ntUsd.negate() : ntUsd;

        if (ntUsdAbs.compareTo(minToStart) < 0) {
            // not enough to start
            return true;
        }
        return false;
    }

    private void resetIfBtmReady() {
        if (bitmexService.getMarketState() == MarketState.READY) {
            final PlaceOrderArgs currArgs = okCoinService.getPlaceOrderArgsRef().getAndSet(null);
            if (okCoinService.noDeferredOrderCheck(currArgs)) {
                return;
            }
            if (okCoinService.btmNotFullyFilledCheck(currArgs)) {
                return;
            }
            okCoinService.resetWaitingArb();
            arbitrageService.resetArbState(okCoinService.getCounterName(), "deferredPlacingPortion");
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private Boolean placeDeferredPortion(BigDecimal block) {

        // check1
        final PlaceOrderArgs currArgs = okCoinService.getPlaceOrderArgsRef().getAndSet(null);
        if (okCoinService.noDeferredOrderCheck(currArgs)) {
            return false;
        }

        // place
        final BigDecimal leftAmount = currArgs.getAmount().subtract(block);
        final PlaceOrderArgs args = currArgs.cloneAsPortion(block);
        okCoinService.beforeDeferredPlacing(args);
        okCoinService.placeOrder(args);

        // check after
        if (leftAmount.signum() > 0) {
            final PlaceOrderArgs leftArgs = args.cloneWithAmount(leftAmount);
            log.error("leftArgs=" + leftArgs);
            if (!okCoinService.getPlaceOrderArgsRef().compareAndSet(null, leftArgs)) {
                log.error("WAITING_ARB: PORTIONS: concurrent placeOrderArgs" + leftArgs);
                warningLogger.error("WAITING_ARB: PORTIONS: concurrent placeOrderArgs" + leftArgs);
            }
        }
        return true;
    }


}
