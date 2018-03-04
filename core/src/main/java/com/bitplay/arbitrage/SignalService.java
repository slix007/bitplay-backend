package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.Settings;

import org.knowm.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Sergey Shurmin on 1/21/18.
 */
@Service
public class SignalService {

    private static final Logger logger = LoggerFactory.getLogger(SignalService.class);
    final ExecutorService executorService = Executors.newFixedThreadPool(2);
    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    public void placeOkexOrderOnSignal(MarketService okexService, Order.OrderType orderType, BigDecimal o_block, BestQuotes bestQuotes, SignalType signalType) {
        final Settings settings = settingsRepositoryService.getSettings();
        final PlacingType okexPlacingType = settings.getOkexPlacingType();
        final PlaceOrderArgs placeOrderArgs = new PlaceOrderArgs(orderType, o_block, bestQuotes,
                okexPlacingType,
                signalType, 1);

        if (settings.getArbScheme() == ArbScheme.CON_B_O) {
            ((OkCoinService) okexService).deferredPlaceOrderOnSignal(placeOrderArgs);
        } else {
            executorService.submit(() -> {
                try {
                    okexService.placeOrder(placeOrderArgs);
                } catch (Exception e) {
                    logger.error("Error on placeOrderOnSignal", e);
                }
            });
        }
    }

    public void placeBitmexOrderOnSignal(MarketService bitmexService, Order.OrderType orderType, BigDecimal b_block, BestQuotes bestQuotes, SignalType signalType) {
        executorService.submit(() -> {
            try {
                bitmexService.placeOrderOnSignal(orderType, b_block, bestQuotes, signalType);
            } catch (Exception e) {
                logger.error("Error on placeOrderOnSignal", e);
            }
        });
    }
}
