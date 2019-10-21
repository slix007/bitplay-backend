package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.CumPersistenceService;
import com.bitplay.persistance.DealPricesRepositoryService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order;
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
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private DealPricesRepositoryService dealPricesRepositoryService;

    @Autowired
    private CumPersistenceService cumPersistenceService;

    @Async("portionsStopCheckExecutor")
    @EventListener(ObChangeEvent.class)
    public void doCheckObChangeEvent() {
        bitmexService.addOoExecutorTask(this::checkForStop);
    }

    private void checkForStop() {
        // 1. check settings enabled
        final Settings settings = settingsRepositoryService.getSettings();
        if (settings.getArbScheme() != ArbScheme.CON_B_O_PORTIONS) {
            // not portions signal
            return;
        }
        if (!settings.getAbortSignal().getAbortSignalPtsEnabled()) {
            // not portions signal
            return;
        }
        final BigDecimal abortSignalPts = settings.getAbortSignal().getAbortSignalPts();
        if (abortSignalPts == null) {
            return; // wrong settings
        }

        // 2. if in progress
        final PlaceOrderArgs currArgs = okCoinService.getPlaceOrderArgsRef().get();
        if (currArgs == null) {
            // no deferred order //TODO ask do we need it
            return;
        }
        final List<FplayOrder> oo = bitmexService.getOnlyOpenFplayOrders();
        if (oo.size() == 0) {
            return; // no in progress
        }

        // 3. delta >= max_border - abort_signal_pts (b_delta или o_delta,в зависимости от начатого сигнала)
        final BigDecimal maxBorder = getMaxBorder(currArgs.getTradeId());
        final DeltaName deltaName = getDeltaName(currArgs);
        final BigDecimal delta = getDelta(deltaName);
        if (maxBorder == null || delta == null) {
            return; // wrong settings
        }

        if (delta.compareTo(maxBorder.add(abortSignalPts)) < 0) {
            incCounters(currArgs, deltaName);
            printSignalAborted(abortSignalPts, currArgs, maxBorder, deltaName, delta);
            bitmexService.cancelAllOrders(oo.get(0), "abort_signal! order", false, false);
        }
    }

    private void incCounters(PlaceOrderArgs currArgs, DeltaName deltaName) {
        final BigDecimal btmFilled = bitmexService.getBtmFilled(currArgs);
        if (btmFilled.signum() == 0) {
            final Long tradeId = currArgs.getTradeId();
            final TradingMode tradingMode = dealPricesRepositoryService.getTradingMode(tradeId);
            if (tradingMode != null) {
                dealPricesRepositoryService.setAbortedSignal(tradeId);
                if (deltaName == DeltaName.B_DELTA) {
                    cumPersistenceService.incAbortedSignalUnstartedVert1(tradingMode);
                } else {
                    cumPersistenceService.incAbortedSignalUnstartedVert2(tradingMode);
                }
            }
        }
    }

    private void printSignalAborted(BigDecimal abortSignalPts, PlaceOrderArgs currArgs, BigDecimal maxBorder, DeltaName deltaName, BigDecimal delta) {
        final String ds = deltaName.getDeltaSymbol();
        final String msg = String.format(
                "#%s signal aborted %s_delta(%s)<%s_max_border(%s) + abort_signal_pts(%s)",
                currArgs.getCounterNameWithPortion(),
                ds, delta,
                ds, maxBorder,
                abortSignalPts);
        okCoinService.getTradeLogger().info(msg);
        bitmexService.getTradeLogger().info(msg);
        log.info(msg);
    }

    private BigDecimal getMaxBorder(Long tradeId) {
        final BtmFokAutoArgs btmFokArgs = dealPricesRepositoryService.findBtmFokAutoArgs(tradeId);
        return btmFokArgs != null ? btmFokArgs.getMaxBorder() : null;
    }

    private DeltaName getDeltaName(PlaceOrderArgs currArgsOkex) {
        final Order.OrderType t = currArgsOkex.getOrderType();
        final boolean okexBuy = t == Order.OrderType.BID || t == Order.OrderType.EXIT_ASK;
        return okexBuy ? DeltaName.B_DELTA : DeltaName.O_DELTA;
    }

    private BigDecimal getDelta(DeltaName deltaName) {
        return deltaName == DeltaName.B_DELTA
                ? arbitrageService.getDelta1()
                : arbitrageService.getDelta2();
    }


}
