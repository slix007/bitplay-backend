package info.bitrich.xchangestream.bitmex;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;

import org.knowm.xchange.bitmex.BitmexExchange;

import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;

/**
 * After this time you'll receive {@link #onDisconnect()} event.
 *
 * To avoid disconnection we have to send keepalive heartbeats(ping).
 */
public class BitmexStreamingExchange extends BitmexExchange implements StreamingExchange {
    private static final String API_URI = "wss://www.bitmex.com:443/realtime";
//    private static final String API_URI = "wss://testnet.bitmex.com/realtime";

    private final BitmexStreamingServiceBitmex streamingService;
    private BitmexStreamingMarketDataService streamingMarketDataService;

    public BitmexStreamingExchange() {
        streamingService = new BitmexStreamingServiceBitmex(API_URI);
    }

    @Override
    protected void initServices() {
        super.initServices();
        streamingMarketDataService = new BitmexStreamingMarketDataService(streamingService);
    }

    @Override
    public Completable connect() {
        return streamingService.connect(1L, TimeUnit.MINUTES);
    }

    @Override
    public Completable onDisconnect() {
        return streamingService.onDisconnect();
    }

    @Override
    public Completable disconnect() {
        return streamingService.disconnect();
    }

    @Override
    public StreamingMarketDataService getStreamingMarketDataService() {
        return streamingMarketDataService;
    }
}
