package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.PlacingType;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Sergey Shurmin on 1/21/18.
 */
@Service
@Slf4j
public class SignalService {

    final ExecutorService executorService = Executors.newFixedThreadPool(2, new NamedThreadFactory("signal-service"));

    @Autowired
    private TradeService tradeService;

    @Autowired
    private OkCoinService okexService;

    @Autowired
    private BitmexService bitmexService;

    public void placeOkexOrderOnSignal(OrderType orderType, BigDecimal o_block, BestQuotes bestQuotes,
                                       PlacingType placingType, String counterName, Long tradeId, Instant lastObTime,
                                       boolean isConBo, Integer portionsQty,
                                       ArbScheme arbScheme) {

        if (o_block.signum() <= 0) {
            executorService.execute(() -> {

                try {
                    Thread.sleep(1000); // let the other market finish it's placing, before set to READY.
                    String warn = "WARNING: o_block=" + o_block + ". No order on signal";
                    okexService.getTradeLogger().warn(warn);
                    log.warn(warn);

                    okexService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));

                } catch (InterruptedException e) {
                    log.error("Error on placeOrderOnSignal", e);
                }

            });

        } else {

            final PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                    .orderType(orderType)
                    .fullAmount(o_block)
                    .amount(o_block)
                    .bestQuotes(bestQuotes)
                    .placingType(placingType)
                    .signalType(SignalType.AUTOMATIC)
                    .attempt(1)
                    .tradeId(tradeId)
                    .counterName(counterName)
                    .lastObTime(lastObTime)
                    .portionsQty(portionsQty)
                    .arbScheme(arbScheme)
                    .build();

            if (isConBo) {
                okexService.deferredPlaceOrderOnSignal(placeOrderArgs);
            } else {
                executorService.submit(() -> {
                    try {
                        tradeService.setOkexStatus(tradeId, TradeMStatus.IN_PROGRESS);
                        okexService.addOoExecutorTask(() -> {
                            TradeResponse tradeResponse = okexService.placeOrder(placeOrderArgs);
                            if (tradeResponse.getErrorCode() != null) {
                                okexService.getTradeLogger().warn("WARNING: " + tradeResponse.getErrorCode());
                                log.warn("WARNING: " + tradeResponse.getErrorCode());
                            }
                        });

                    } catch (Exception e) {
                        log.error("Error on placeOrderOnSignal", e);
                    }
                });
            }
        }
    }

    public void placeBitmexOrderOnSignal(OrderType orderType, BigDecimal b_block, BestQuotes bestQuotes,
            PlacingType placingType, String counterName, Long tradeId, Instant lastObTime, boolean isConBo, BtmFokAutoArgs btmFokAutoArgs) {

        executorService.submit(() -> {
            try {

                if (b_block.signum() <= 0) {
                    Thread.sleep(1000);
                    String warn = "WARNING: b_block=" + b_block + ". No order on signal";
                    bitmexService.getTradeLogger().warn(warn);
                    log.warn(warn);

                    bitmexService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));
                    return;
                }

                final int attempt = isConBo ? PlaceOrderArgs.NO_REPEATS_ATTEMPT : 1;
                final PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                        .orderType(orderType)
                        .fullAmount(b_block)
                        .amount(b_block)
                        .bestQuotes(bestQuotes)
                        .placingType(placingType)
                        .signalType(SignalType.AUTOMATIC)
                        .attempt(attempt)
                        .tradeId(tradeId)
                        .counterName(counterName)
                        .lastObTime(lastObTime)
                        .btmFokArgs(btmFokAutoArgs)
                        .build();

                tradeService.setBitmexStatus(tradeId, TradeMStatus.IN_PROGRESS);
                bitmexService.addOoExecutorTask(() -> bitmexService.placeOrder(placeOrderArgs));

            } catch (Exception e) {
                log.error("Error on placeOrderOnSignal", e);
            }
        });
    }
}
