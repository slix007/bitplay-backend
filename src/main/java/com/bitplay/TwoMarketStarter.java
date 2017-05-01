package com.bitplay;

import com.bitplay.market.MarketService;
import com.bitplay.market.arbitrage.ArbitrageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("twomarketstarter")
public class TwoMarketStarter {

    private Config config;
    private ApplicationContext context;

    private MarketService firstMarketService;
    private MarketService secondMarketService;

    private ArbitrageService arbitrageService;
    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    public TwoMarketStarter(ApplicationContext context,
                            Config config) {
        this.context = context;
        this.config = config;
    }

    @PostConstruct
    private void init() {
        final String firstMarketName = config.getFirstMarketName();
        firstMarketService = (MarketService) context.getBean(firstMarketName);
        firstMarketService.initializeMarket(config.getFirstMarketKey(), config.getFirstMarketSecret());
        System.out.println("MARKET1: " + firstMarketService);

        final String secondMarketName = config.getSecondMarketName();
        secondMarketService = (MarketService) context.getBean(secondMarketName);
        secondMarketService.initializeMarket(config.getSecondMarketKey(), config.getSecondMarketSecret());
        System.out.println("MARKET2: " + secondMarketService);

        arbitrageService.init(this);
    }

    public MarketService getFirstMarketService() {
        return firstMarketService;
    }

    public MarketService getSecondMarketService() {
        return secondMarketService;
    }
}
