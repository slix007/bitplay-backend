package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.PlBefore;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.xchange.dto.Order.OrderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Sergey Shurmin on 1/21/18.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SignalService {

    private final TradeService tradeService;

    public CompletableFuture<Void> placeOrderOnSignal(MarketServicePreliq marketService, OrderType orderType, BigDecimal block, BestQuotes bestQuotes,
                                                      PlacingType placingType, String counterName, Long tradeId,
                                                      boolean isConBo, Integer portionsQty,
                                                      ArbScheme arbScheme, PlBefore beforeSignalMetrics, BtmFokAutoArgs btmFokAutoArgs) {
        CompletableFuture<Void> promise;
        if (marketService.getArbType() == ArbType.LEFT) {
            // bitmex or okex
            promise = placeLeftOrderOnSignal(marketService, orderType, block, bestQuotes, placingType, counterName, tradeId, isConBo,
                    beforeSignalMetrics, btmFokAutoArgs);
        } else {
            // always okex
            promise = placeRightOrderOnSignal((OkCoinService) marketService, orderType, block, bestQuotes, placingType, counterName, tradeId, isConBo,
                    portionsQty, arbScheme);
        }
        return promise;
    }

    private CompletableFuture<Void> placeRightOrderOnSignal(OkCoinService okexService, OrderType orderType, BigDecimal o_block, BestQuotes bestQuotes,
                                                            PlacingType placingType, String counterName, Long tradeId,
                                                            boolean isConBo, Integer portionsQty,
                                                            ArbScheme arbScheme) {
        CompletableFuture<Void> promise = CompletableFuture.completedFuture(null);
        try {
            if (o_block.signum() <= 0) {

                Thread.sleep(1000); // let the other market finish it's placing, before set to READY.
                String warn = "WARNING: o_block=" + o_block + ". No order on signal";
                okexService.getTradeLogger().warn(warn);
                log.warn(warn);

                okexService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));

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
                        .portionsQty(portionsQty)
                        .arbScheme(arbScheme)
                        .build();

                if (isConBo) {
                    okexService.deferredPlaceOrderOnSignal(placeOrderArgs);
                } else {
                    tradeService.setOkexStatus(tradeId, TradeMStatus.IN_PROGRESS);
                    promise = okexService.addOoExecutorTask(() -> {
                        TradeResponse tradeResponse = okexService.placeOrder(placeOrderArgs);
                        if (tradeResponse.getErrorCode() != null) {
                            okexService.getTradeLogger().warn("WARNING: " + tradeResponse.getErrorCode());
                            log.warn("WARNING: " + tradeResponse.getErrorCode());
                        }
                    });

                }
            }
        } catch (InterruptedException e) {
            log.error("Error on placeOrderOnSignal", e);
            promise = CompletableFuture.completedFuture(null);
        }
        return promise;
    }

    private CompletableFuture<Void> placeLeftOrderOnSignal(MarketServicePreliq left, OrderType orderType, BigDecimal b_block, BestQuotes bestQuotes,
                                                           PlacingType placingType, String counterName, Long tradeId, boolean isConBo,
                                                           PlBefore beforeSignalMetrics,
                                                           BtmFokAutoArgs btmFokAutoArgs) {

        CompletableFuture<Void> promise = CompletableFuture.completedFuture(null);
        try {

            if (b_block.signum() <= 0) {
                Thread.sleep(1000);
                String warn = "WARNING: L_block=" + b_block + ". No order on signal";
                left.getTradeLogger().warn(warn);
                log.warn(warn);

                left.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));
                return promise;
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
                    .plBefore(beforeSignalMetrics)
                    .btmFokArgs(btmFokAutoArgs)
                    .build();

            tradeService.setBitmexStatus(tradeId, TradeMStatus.IN_PROGRESS);
//            beforeSignalMetrics.setAddPlacingTask(Instant.now());
            promise = left.addOoExecutorTask(() -> {
//                beforeSignalMetrics.setStartPlacingTask(Instant.now());
                left.placeOrder(placeOrderArgs);
            });

        } catch (Exception e) {
            log.error("Error on placeOrderOnSignal", e);
            promise = CompletableFuture.completedFuture(null);
        }
        return promise;
    }
}
