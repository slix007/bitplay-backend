package com.bitplay.xchange.bitmex;

import com.bitplay.xchange.bitmex.service.BitmexTradeService;
import com.bitplay.xchange.BaseExchange;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.ExchangeSpecification;
import com.bitplay.xchange.bitmex.service.BitmexAccountService;
import com.bitplay.xchange.bitmex.service.BitmexMarketDataService;
import com.bitplay.xchange.bitmex.service.BitmexStateService;
import com.bitplay.xchange.utils.nonce.TimestampIncrementingNonceFactory;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexExchange extends BaseExchange implements Exchange {

    private SynchronizedValueFactory<Long> nonceFactory = new TimestampIncrementingNonceFactory();

    private final BitmexStateService bitmexStateService = new BitmexStateService();

    @Override
    protected void initServices() {
        this.marketDataService = new BitmexMarketDataService(this);
        this.accountService = new BitmexAccountService(this);
        this.tradeService = new BitmexTradeService(this);
    }

    public ExchangeSpecification getDefaultExchangeSpecification() {
        ExchangeSpecification exchangeSpecification = new ExchangeSpecification(this.getClass().getCanonicalName());
        exchangeSpecification.setSslUri("https://www.bitmex.com");
        exchangeSpecification.setHost("www.bitmex.com");
        exchangeSpecification.setPort(443);
        exchangeSpecification.setExchangeName("Bitmex");
        exchangeSpecification.setExchangeDescription("Bitmex is a Bitcoin exchange");
        exchangeSpecification.setMetaDataJsonFileOverride(null);

        return exchangeSpecification;
    }

    @Override
    public SynchronizedValueFactory<Long> getNonceFactory() {
        return nonceFactory;
    }

    public BitmexStateService getBitmexStateService() {
        return bitmexStateService;
    }
}
