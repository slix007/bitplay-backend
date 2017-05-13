package org.knowm.xchange.bitmex;

import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitmex.service.BitmexAccountService;
import org.knowm.xchange.bitmex.service.BitmexMarketDataService;
import org.knowm.xchange.utils.nonce.TimestampIncrementingNonceFactory;

import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexExchange extends BaseExchange implements Exchange {

    private SynchronizedValueFactory<Long> nonceFactory = new TimestampIncrementingNonceFactory();

    @Override
    protected void initServices() {
        this.marketDataService = new BitmexMarketDataService(this);
        this.accountService = new BitmexAccountService(this);
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

}
