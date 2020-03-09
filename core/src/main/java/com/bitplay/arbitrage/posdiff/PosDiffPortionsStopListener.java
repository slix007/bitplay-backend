package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.CumPersistenceService;
import com.bitplay.persistance.DealPricesRepositoryService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.dealprices.FactPrice;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class PosDiffPortionsStopListener {

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private DealPricesRepositoryService dealPricesRepositoryService;

    @Autowired
    private CumPersistenceService cumPersistenceService;

    @Async("portionsStopCheckExecutor")
    @EventListener(ObChangeEvent.class)
    public void doCheckObChangeEvent() {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        if (left != null) {
            left.addOoExecutorTask(this::checkForStopTask);
        }
    }

    private void checkForStopTask() {
        try {
            checkForStop();
        } catch (Exception e) {
            log.error("portionsStopError", e);
        }
    }

    private void checkForStop() throws Exception {
        // 1. check settings enabled
        final Settings settings = settingsRepositoryService.getSettings();
        if (!settings.getAbortSignal().getAbortSignalPtsEnabled()) {
            // not portions signal
            return;
        }
        final BigDecimal abortSignalPts = settings.getAbortSignal().getAbortSignalPts();
        if (abortSignalPts == null) {
            return; // wrong settings
        }

        // 2. if in progress
        final OkCoinService right = (OkCoinService) arbitrageService.getRightMarketService();
        final PlaceOrderArgs currArgs = right.getPlaceOrderArgsRef().get();
        if (currArgs == null) {
            // no deferred order //TODO ask do we need it
            return;
        }
        if (currArgs.getArbScheme() != ArbScheme.R_wait_L_portions) {
            // not portions signal
            return;
        }
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final List<FplayOrder> oo = left.getOnlyOpenFplayOrdersClone();
        if (oo.size() == 0) {
            return; // no in progress
        }

        // 3. delta >= max_border - abort_signal_pts (b_delta или o_delta,в зависимости от начатого сигнала)
        final BigDecimal maxBorder = getMaxBorder(currArgs.getTradeId());
        final DeltaName deltaName = currArgs.getDeltaName();
        final BigDecimal delta = getDelta(deltaName);
        if (maxBorder == null || delta == null) {
            return; // wrong settings
        }

        if (delta.compareTo(maxBorder.add(abortSignalPts)) < 0) {

            left.cancelAllOrders(oo.get(0), "abort_signal! order", false, false);

            final BigDecimal btmFilled = left.getLeftFilledAndUpdateBPriceFact(currArgs, false);
            final FactPrice bPriceFact = dealPricesRepositoryService.getFullDealPrices(currArgs.getTradeId()).getBPriceFact();
            if (btmFilled.compareTo(bPriceFact.getFullAmount()) < 0) {
                final boolean cntUpdated = incCounters(currArgs, deltaName, btmFilled);
                printSignalAborted(abortSignalPts, currArgs, maxBorder, deltaName, delta, btmFilled, cntUpdated);
            } else {
                // fully filled. Means bitmex was filled before 'cancel request'.
                // Do nothing.
            }
        }
    }

    private boolean incCounters(PlaceOrderArgs currArgs, DeltaName deltaName, BigDecimal btmFilled) {
        if (btmFilled.signum() == 0) {
            final Long tradeId = currArgs.getTradeId();
            final TradingMode tradingMode = dealPricesRepositoryService.getTradingMode(tradeId);
            if (tradingMode != null) {
                final boolean abortedSignal = dealPricesRepositoryService.isAbortedSignal(tradeId);
                if (!abortedSignal) {
                    dealPricesRepositoryService.setAbortedSignal(tradeId);
                    if (deltaName == DeltaName.B_DELTA) {
                        cumPersistenceService.incAbortedSignalUnstartedVert1(tradingMode);
                    } else {
                        cumPersistenceService.incAbortedSignalUnstartedVert2(tradingMode);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void printSignalAborted(BigDecimal abortSignalPts, PlaceOrderArgs currArgs, BigDecimal maxBorder, DeltaName deltaName, BigDecimal delta,
                                    BigDecimal btmFilled, boolean cntUpdated) {
        final String ds = deltaName.getDeltaSymbol();
        final String msg = String.format(
                "#%s signal aborted %s_delta(%s)<%s_max_border(%s) + abort_signal_pts(%s); btmFilled=%s; aborted_counter_updated=%s",
                currArgs.getCounterNameWithPortion(),
                ds, delta,
                ds, maxBorder,
                abortSignalPts,
                btmFilled,
                cntUpdated);
        arbitrageService.getLeftMarketService().getTradeLogger().info(msg);
        arbitrageService.getRightMarketService().getTradeLogger().info(msg);
        tradeService.info(currArgs.getTradeId(), currArgs.getCounterNameWithPortion(), msg);
        log.info(msg);
    }

    private BigDecimal getMaxBorder(Long tradeId) {
        final BtmFokAutoArgs btmFokArgs = dealPricesRepositoryService.findBtmFokAutoArgs(tradeId);
        return btmFokArgs != null ? btmFokArgs.getMaxBorder() : null;
    }

    private BigDecimal getDelta(DeltaName deltaName) {
        return deltaName == DeltaName.B_DELTA
                ? arbitrageService.getDelta1()
                : arbitrageService.getDelta2();
    }


}
