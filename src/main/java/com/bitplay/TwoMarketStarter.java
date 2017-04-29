package com.bitplay;

import com.bitplay.market.MarketService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.polonex.PoloniexService;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service
public class TwoMarketStarter {

    //    @Autowired
    private Config config;

    //    @Autowired
    private ApplicationContext context;
//    public void context(ApplicationContext context) { this.context = context; }

    private PoloniexService poloniexService;
    private OkCoinService okCoinService;
    private BitmexService bitmexService;

    private MarketService firstMarketService;
    private MarketService secondMarketService;

    public TwoMarketStarter(Config config, PoloniexService poloniexService, OkCoinService okCoinService, BitmexService bitmexService) {
        this.config = config;
        this.poloniexService = poloniexService;
        this.okCoinService = okCoinService;
        this.bitmexService = bitmexService;
        init();
    }

    private void init() {
        final String firstMarketName = config.getFirstMarketName();
        final MarketService bean = chooseMarketService(firstMarketName);
        System.out.println("MARKET1: " + bean);
    }

    private MarketService chooseMarketService(String name) {
        MarketService marketService;
        switch (name) {
            case "poloniex":
                marketService = poloniexService;
                break;
            case "okcoin":
                marketService = okCoinService;
                break;
            case "bitmex":
                marketService = bitmexService;
                break;
            default:
                throw new IllegalArgumentException("No such market implementation " + name);

        }
        return marketService;
    }

}
