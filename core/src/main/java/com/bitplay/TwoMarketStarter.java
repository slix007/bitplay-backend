package com.bitplay;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import javax.annotation.PostConstruct;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service
public class TwoMarketStarter {

    private final static Logger logger = LoggerFactory.getLogger(TwoMarketStarter.class);

    private Config config;
    private ApplicationContext context;

    private MarketService firstMarketService;
    private MarketService secondMarketService;
    private PosDiffService posDiffService;

    private ArbitrageService arbitrageService;
    private RestartService restartService;
    public TwoMarketStarter(ApplicationContext context,
                            Config config) {
        this.context = context;
        this.config = config;
    }

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    @Autowired
    public void setRestartService(RestartService restartService) {
        this.restartService = restartService;
    }

    @PostConstruct
    private void init() {

        restartService.scheduleCheckForFullStart();

        try {
            final String firstMarketName = config.getFirstMarketName();
            firstMarketService = (MarketService) context.getBean(firstMarketName);
            firstMarketService.init(config.getFirstMarketKey(), config.getFirstMarketSecret());
            logger.info("MARKET1: " + firstMarketService);
        } catch (Exception e) {
            logger.error("Initialization error", e);
            // Workaround to make work the other market
            firstMarketService.getPosition().setPositionLong(BigDecimal.ZERO);
            firstMarketService.getPosition().setPositionShort(BigDecimal.ZERO);
            firstMarketService.setMarketState(MarketState.STOPPED);
        }

        try {
            final String secondMarketName = config.getSecondMarketName();
            secondMarketService = (MarketService) context.getBean(secondMarketName);
            secondMarketService.init(config.getSecondMarketKey(), config.getSecondMarketSecret());
            logger.info("MARKET2: " + secondMarketService);
        } catch (Exception e) {
            logger.error("Initialization error", e);
            // Workaround to make work the other market
            secondMarketService.getPosition().setPositionLong(BigDecimal.ZERO);
            secondMarketService.getPosition().setPositionShort(BigDecimal.ZERO);
            secondMarketService.setMarketState(MarketState.STOPPED);
        }

        try {
            final String correctPosition = "pos-diff";
            posDiffService = (PosDiffService) context.getBean(correctPosition);
            logger.info("PosDiffService: " + posDiffService);
            arbitrageService.init(this);
        } catch (Exception e) {
            logger.error("Initialization error", e);
        }
    }

    public MarketService getFirstMarketService() {
        return firstMarketService;
    }

    public MarketService getSecondMarketService() {
        return secondMarketService;
    }

    public PosDiffService getPosDiffService() {
        return posDiffService;
    }
}
