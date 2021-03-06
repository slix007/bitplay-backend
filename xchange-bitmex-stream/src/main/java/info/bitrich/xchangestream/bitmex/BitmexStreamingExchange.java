package info.bitrich.xchangestream.bitmex;

import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradingService;

import org.knowm.xchange.bitmex.BitmexExchange;

import java.net.URISyntaxException;

import io.reactivex.Completable;

/**
 * After this time you'll receive {@link #onDisconnect()} event.
 *
 * To avoid disconnection we have to send keepalive heartbeats(ping).
 */
public class BitmexStreamingExchange extends BitmexExchange implements StreamingExchange {
    private static final String API_URI = "wss://www.bitmex.com:443/realtime";
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

    @Override
    public Completable connect() {
        return streamingService.connect();
    }

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
}
