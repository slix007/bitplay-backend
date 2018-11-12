package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.Settings;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.knowm.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 1/21/18.
 */
@Service
public class SignalService {

    private static final Logger logger = LoggerFactory.getLogger(SignalService.class);
    final ExecutorService executorService = Executors.newFixedThreadPool(2);
    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private SlackNotifications slackNotifications;

    public void placeOkexOrderOnSignal(MarketService okexService, Order.OrderType orderType, BigDecimal o_block, BestQuotes bestQuotes,
            SignalType signalType, PlacingType placingType, String counterName, Long tradeId, Instant lastObTime) {

        if (o_block.signum() <= 0) {
            executorService.execute(() -> {

                try {
                    Thread.sleep(1000);
                    String warn = "WARNING: o_block=" + o_block + ". No order on signal";
                    okexService.getTradeLogger().warn(warn);
                    logger.warn(warn);

                    okexService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));

                } catch (InterruptedException e) {
                    logger.error("Error on placeOrderOnSignal", e);
                }

            });

        } else {

            final Settings settings = settingsRepositoryService.getSettings();
            final PlaceOrderArgs placeOrderArgs = new PlaceOrderArgs(orderType, o_block, bestQuotes,
                    placingType,
                    signalType, 1, tradeId, counterName, lastObTime);

            if (settings.getArbScheme() == ArbScheme.CON_B_O) {
                ((OkCoinService) okexService).deferredPlaceOrderOnSignal(placeOrderArgs);
            } else {
                executorService.submit(() -> {
                    try {
                        tradeService.setOkexStatus(tradeId, TradeMStatus.IN_PROGRESS);
                        TradeResponse tradeResponse = okexService.placeOrder(placeOrderArgs);

                        if (tradeResponse.getErrorCode() != null) {
                            okexService.getTradeLogger().warn("WARNING: " + tradeResponse.getErrorCode());
                            logger.warn("WARNING: " + tradeResponse.getErrorCode());
                        }

                    } catch (Exception e) {
                        logger.error("Error on placeOrderOnSignal", e);
                    }
                });
            }
        }
    }

    public void placeBitmexOrderOnSignal(MarketService bitmexService, Order.OrderType orderType, BigDecimal b_block, BestQuotes bestQuotes,
            SignalType signalType, PlacingType placingType, String counterName, Long tradeId, Instant lastObTime) {

        executorService.submit(() -> {
            try {

                slackNotifications.sendNotify(String.format("#%s signal placeOrder bitmex %s a=%s", counterName, orderType, b_block));

                if (b_block.signum() <= 0) {
                    Thread.sleep(1000);
                    String warn = "WARNING: b_block=" + b_block + ". No order on signal";
                    bitmexService.getTradeLogger().warn(warn);
                    logger.warn(warn);

                    bitmexService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));
                    return;
                }

                final PlaceOrderArgs placeOrderArgs = new PlaceOrderArgs(orderType, b_block, bestQuotes, placingType, signalType, 1,
                        tradeId, counterName, lastObTime);

                tradeService.setBitmexStatus(tradeId, TradeMStatus.IN_PROGRESS);
                ((BitmexService) bitmexService).placeOrderToOpenOrders(placeOrderArgs);

            } catch (Exception e) {
                logger.error("Error on placeOrderOnSignal", e);
            }
        });
    }
}
