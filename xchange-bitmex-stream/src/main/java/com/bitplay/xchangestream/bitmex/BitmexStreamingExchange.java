package com.bitplay.xchangestream.bitmex;

import com.bitplay.xchange.ExchangeSpecification;
import com.bitplay.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import com.bitplay.core.StreamingAccountService;
import com.bitplay.core.StreamingExchangeEx;
import com.bitplay.core.StreamingMarketDataService;
import com.bitplay.core.StreamingPrivateDataService;
import com.bitplay.core.StreamingTradingService;
import com.bitplay.service.ws.statistic.PingStatEvent;
import io.reactivex.Completable;
import io.reactivex.Observable;
import java.net.URISyntaxException;
import com.bitplay.xchange.bitmex.BitmexExchange;

/**
 * After this time you'll receive {@link #onDisconnect()} event.
 *
 * To avoid disconnection we have to send keepalive heartbeats(ping).
 */
public class BitmexStreamingExchange extends BitmexExchange implements StreamingExchangeEx {
    private StreamingServiceBitmex streamingService;
    private BitmexStreamingMarketDataService streamingMarketDataService;
    private BitmexStreamingAccountService streamingAccountService;
    private BitmexStreamingTradingService streamingTradingService;

    public BitmexStreamingExchange() throws URISyntaxException {
    }

    @Override
    protected void initServices() {
        super.initServices();
        final ExchangeSpecification exchangeSpec = getExchangeSpecification();
        final String apiUrl = (String) exchangeSpec.getExchangeSpecificParametersItem(API_URL);
        streamingService = new StreamingServiceBitmex(apiUrl);
        streamingMarketDataService = new BitmexStreamingMarketDataService(streamingService);
        streamingAccountService = new BitmexStreamingAccountService(streamingService);
        streamingTradingService = new BitmexStreamingTradingService(streamingService);
    }

//    @Override
//    public Completable connect() {
//        return streamingService.connect();
//    }

    @Override
    public Completable onDisconnect() {
        return streamingService.onDisconnect();
    }

    public Completable authenticate() {
        return streamingService.authenticate(
                exchangeSpecification.getApiKey(),
                exchangeSpecification.getSecretKey(),
                getNonceFactory().createValue()
        );
    }

    @Override
    public Completable disconnect() {
        return streamingService.disconnect();
    }

    @Override
    public StreamingMarketDataService getStreamingMarketDataService() {
        return streamingMarketDataService;
    }

    @Override
    public StreamingAccountService getStreamingAccountService() {
        return streamingAccountService;
    }

    @Override
    public StreamingTradingService getStreamingTradingService() {
        return streamingTradingService;
    }

    ////
    @Override
    public StreamingPrivateDataService getStreamingPrivateDataService() {
        return null;
    }

    @Override
    public Completable connect() {
        return streamingService.connect();
    }

    @Override
    public boolean isAlive() {
        return false;
    }

    @Override
    public void useCompressedMessages(boolean b) {

    }

    public Observable<PingStatEvent> subscribePingStats() {
        return streamingService.subscribePingStats();
    }

}
