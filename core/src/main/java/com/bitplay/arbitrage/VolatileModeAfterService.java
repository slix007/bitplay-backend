package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.market.MarketService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.settings.BitmexChangeOnSoService;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VolatileModeAfterService {

    private final ExecutorService executorServiceBtm = Executors.newSingleThreadExecutor(new NamedThreadFactory("volatile-mode-after-btm"));
    private final ExecutorService executorServiceOkex = Executors.newSingleThreadExecutor(new NamedThreadFactory("volatile-mode-after-okex"));
    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private SignalService signalService;

    @Autowired
    private OkCoinService okexService;

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private BitmexChangeOnSoService bitmexChangeOnSoService;

    @Autowired
    private TradeService fplayTradeService;

    void justSetVolatileMode(Long tradeId, BtmFokAutoArgs btmFokAutoArgs) {
        final List<FplayOrder> bitmexOO = bitmexService.getOnlyOpenFplayOrders();
        final List<FplayOrder> okexOO = okexService.getOnlyOpenFplayOrders();

        // case 1. На обоих биржах выставлены лимитные ордера
        // case 3. На Bitmex System_overloaded, на Okex выставлен лимитный ордер.
//        if (bitmexOO.size() > 0 && okexOO.size() > 0) {
//            executorService.execute(() -> replaceLimitOrdersBitmex(bitmexOO));
//            executorService.execute(() -> replaceLimitOrdersOkex(okexOO));
//        }
        // case 2. На Bitmex выставлен лимитный ордер, Okex в статусе "Waiting Arb".
        // case 4. На Bitmex "System_overloaded", на Okex "Waiting_arb".
//        if (bitmexOO.size() > 0 && okexService.getMarketState() == MarketState.WAITING_ARB) {
//            executorService.execute(() -> replaceLimitOrdersBitmex(bitmexOO));
//             OK while VolatileMode has no 'Arbitrage version'
//        }

        final PlacingType okexPlacingType = settingsRepositoryService.getSettings().getOkexPlacingType();
        okexService.changeDeferredPlacingType(okexPlacingType);

        if (bitmexOO.size() > 0) {
            executorServiceBtm.execute(() -> replaceLimitOrdersBitmex(bitmexOO, tradeId, btmFokAutoArgs));
        }
        if (okexOO.size() > 0) {
            executorServiceOkex.execute(() -> replaceLimitOrdersOkex(okexOO, tradeId));
        }
    }

    private void replaceLimitOrdersBitmex(List<FplayOrder> bitmexOO, Long tradeId, BtmFokAutoArgs btmFokAutoArgs) {
        while (bitmexService.getMarketState() == MarketState.SYSTEM_OVERLOADED) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error("Sleep error", e);
                return;
            }
            if (bitmexService.isMarketStopped()) {
                return;
            }
        }

        final PlacingType btmPlacingType = bitmexChangeOnSoService.getPlacingType();

        replaceLimitOrders(bitmexService, btmPlacingType, bitmexOO, tradeId, btmFokAutoArgs);
    }

    private void replaceLimitOrdersOkex(List<FplayOrder> okexOO, Long tradeId) {
        final PlacingType okexPlacingType = settingsRepositoryService.getSettings().getOkexPlacingType();
        replaceLimitOrders(okexService, okexPlacingType, okexOO, tradeId, null);
    }

    private void replaceLimitOrders(MarketService marketService, PlacingType placingType, List<FplayOrder> currOrders, Long tradeId, BtmFokAutoArgs btmFokAutoArgs) {
        if (placingType != PlacingType.MAKER && placingType != PlacingType.MAKER_TICK) {

            final MarketState prevState = marketService.getMarketState();
            // 1. cancel and set marketState=PLACING_ORDER
            final String counterName = marketService.gerCurrCounterName(currOrders);
            final FplayOrder stub = new FplayOrder(marketService.getMarketId(), tradeId, counterName);

            final List<LimitOrder> orders = marketService.cancelAllOrders(stub, "VolatileMode activated: CancelAllOpenOrders", true);
            final BigDecimal amountDiff = orders.stream()
                    .map(o -> {
                        final BigDecimal am = o.getTradableAmount().subtract(o.getCumulativeAmount());
                        final boolean sellType = o.getType() == OrderType.ASK || o.getType() == OrderType.EXIT_BID;
                        return sellType ? am.negate() : am;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 2. place
            if (amountDiff.signum() != 0) {

                fplayTradeService.setTradingMode(tradeId, TradingMode.CURRENT_VOLATILE);
                fplayTradeService.info(tradeId, counterName, String.format("#%s change Trading mode to current-volatile", counterName));

                final OrderType orderType = amountDiff.signum() > 0 ? OrderType.BID : OrderType.ASK;
                final BigDecimal amountLeft = amountDiff.abs();
                BestQuotes bestQuotes = getBestQuotes(currOrders);

                if (marketService.getName().equals(BitmexService.NAME)) {
                    signalService.placeBitmexOrderOnSignal(orderType, amountLeft, bestQuotes, placingType, counterName, tradeId, null, false,
                            btmFokAutoArgs);
                } else {
                    signalService.placeOkexOrderOnSignal(orderType, amountLeft, bestQuotes, placingType, counterName, tradeId, null, false);
                }
            } else {
                log.warn("VolatileMode activated: no amountLeft.");
                marketService.getTradeLogger().warn("VolatileMode activated: no amountLeft.");
                marketService.setMarketState(prevState);
            }
        }
    }

    private BestQuotes getBestQuotes(List<FplayOrder> orders) {
        return orders.stream()
                .map(FplayOrder::getBestQuotes)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
