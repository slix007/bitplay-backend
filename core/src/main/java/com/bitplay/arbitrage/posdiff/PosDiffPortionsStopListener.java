package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.arbitrage.events.SigEvent;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.DealPricesRepositoryService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.Settings;
import org.knowm.xchange.dto.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

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

    @Async("portionsStopCheckExecutor")
    @EventListener(ObChangeEvent.class)
    public void doCheckObChangeEvent(ObChangeEvent obChangeEvent) {
        final SigEvent sigEvent = obChangeEvent.getSigEvent();
        checkForStop();
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
        final BigDecimal delta = getDelta(currArgs);
        if (maxBorder == null || delta == null) {
            return; // wrong settings
        }

        if (delta.compareTo(maxBorder.subtract(abortSignalPts)) < 0) {
            //TODO check it works
            bitmexService.cancelAllOrders(oo.get(0), "abort_signal", false);
        }
    }

    private BigDecimal getMaxBorder(Long tradeId) {
        final BtmFokAutoArgs btmFokArgs = dealPricesRepositoryService.findBtmFokAutoArgs(tradeId);
        return btmFokArgs != null ? btmFokArgs.getMaxBorder() : null;
    }

    private BigDecimal getDelta(PlaceOrderArgs currArgsOkex) {
        final Order.OrderType t = currArgsOkex.getOrderType();
        final boolean okexBuy = t == Order.OrderType.BID || t == Order.OrderType.EXIT_ASK;
        return okexBuy ? arbitrageService.getDelta1() : arbitrageService.getDelta2(); //TODO check it
    }


}
