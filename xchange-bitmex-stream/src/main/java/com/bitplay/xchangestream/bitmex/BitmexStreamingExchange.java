package com.bitplay.xchangestream.bitmex;

import com.bitplay.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import com.bitplay.core.ProductSubscription;
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
    // old api.
    // slow  since 1 Nov 2021.
    // obsolete since 1 Feb 2022.
//    private static final String API_URI = "wss://www.bitmex.com:443/realtime";

    // https://blog.bitmex.com/api_announcement/change-of-websocket-endpoint/
    // since 15 Oct 2021
//    private static final String API_URI = "wss://ws.bitmex.com/realtime";
    private static final String API_URI = "wss://api.direct.bitmex.com/realtime";
//    private static final String API_URI = "wss://testnet.bitmex.com/realtime";

    private final StreamingServiceBitmex streamingService;
    private BitmexStreamingMarketDataService streamingMarketDataService;
    private BitmexStreamingAccountService streamingAccountService;
    private BitmexStreamingTradingService streamingTradingService;

    public BitmexStreamingExchange() throws URISyntaxException {
        streamingService = new StreamingServiceBitmex(API_URI);
    }

    @Override
    protected void initServices() {
        super.initServices();
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
    public Completable connect(ProductSubscription... productSubscriptions) {
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
