package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
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
public class SignalVolatileModeService {

    final ExecutorService executorService = Executors.newFixedThreadPool(2,
            new NamedThreadFactory("volatile-mode-service"));
    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private SignalService signalService;

    @Autowired
    private OkCoinService okexService;

    @Autowired
    private BitmexService bitmexService;

    public void justSetVolatileMode() {
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
        if (bitmexOO.size() > 0) {
            executorService.execute(() -> replaceLimitOrdersBitmex(bitmexOO));
        }
        if (okexOO.size() > 0) {
            executorService.execute(() -> replaceLimitOrdersOkex(okexOO));
        }
    }

    private void replaceLimitOrdersBitmex(List<FplayOrder> bitmexOO) {
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

        final PlacingType placingType = settingsRepositoryService.getSettings().getBitmexPlacingType();
        replaceLimitOrders(bitmexService, placingType, bitmexOO);
    }

    private void replaceLimitOrdersOkex(List<FplayOrder> okexOO) {
        final PlacingType placingType = settingsRepositoryService.getSettings().getOkexPlacingType();
        replaceLimitOrders(okexService, placingType, okexOO);
    }

    private void replaceLimitOrders(MarketService marketService, PlacingType placingType, List<FplayOrder> currOrders) {
        if (placingType != PlacingType.MAKER && placingType != PlacingType.MAKER_TICK) {
            // 1. cancel
            final List<LimitOrder> orders = marketService.cancelAllOrders("VolatileMode activated: CancelAllOpenOrders");
            final BigDecimal amountLeft = orders.stream()
                    .map(o -> {
                        final BigDecimal am = o.getTradableAmount().subtract(o.getCumulativeAmount());
                        final boolean sellType = o.getType() == OrderType.ASK || o.getType() == OrderType.EXIT_BID;
                        return sellType ? am.negate() : am;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 2. place
            if (amountLeft.signum() != 0) {
                final OrderType orderType = amountLeft.signum() > 0 ? OrderType.BID : OrderType.ASK;
                BestQuotes bestQuotes = getBestQuotes(currOrders);

                if (marketService.getName().equals(BitmexService.NAME)) {
                    signalService.placeBitmexOrderOnSignal(orderType, amountLeft,
                            bestQuotes,
                            placingType,
                            marketService.getCounterName(),
                            marketService.getArbitrageService().getTradeId(),
                            null
                    );
                } else {
                    signalService.placeOkexOrderOnSignal(orderType, amountLeft,
                            bestQuotes,
                            placingType,
                            okexService.getCounterName(),
                            okexService.getArbitrageService().getTradeId(),
                            null
                    );
                }
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