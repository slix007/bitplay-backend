package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
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

    public void placeOkexOrderOnSignal(MarketService okexService, Order.OrderType orderType, BigDecimal o_block, BestQuotes bestQuotes,
            SignalType signalType, PlacingType placingType, String counterName, Instant lastObTime) {
        final Settings settings = settingsRepositoryService.getSettings();
        final PlaceOrderArgs placeOrderArgs = new PlaceOrderArgs(orderType, o_block, bestQuotes,
                placingType,
                signalType, 1, counterName, lastObTime);

        if (settings.getArbScheme() == ArbScheme.CON_B_O) {
            ((OkCoinService) okexService).deferredPlaceOrderOnSignal(placeOrderArgs);
        } else {
            executorService.submit(() -> {
                try {
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

    public void placeBitmexOrderOnSignal(MarketService bitmexService, Order.OrderType orderType, BigDecimal b_block, BestQuotes bestQuotes,
            SignalType signalType, PlacingType placingType, String counterName, Instant lastObTime) {
        executorService.submit(() -> {
            try {

                final PlaceOrderArgs placeOrderArgs = new PlaceOrderArgs(orderType, b_block, bestQuotes, placingType, signalType, 1, counterName, lastObTime);

                ((BitmexService) bitmexService).placeOrderToOpenOrders(placeOrderArgs);
            } catch (Exception e) {
                logger.error("Error on placeOrderOnSignal", e);
            }
        });
    }
}
