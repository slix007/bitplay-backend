package info.bitrich.xchangestream.bitmex;

import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingTradingService;

import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.dto.trade.OpenOrders;

import io.reactivex.Observable;

/**
 * Created by Sergey Shurmin on 5/17/17.
 */
public class BitmexStreamingTradingService implements StreamingTradingService {

    private final StreamingServiceBitmex service;

    public BitmexStreamingTradingService(StreamingServiceBitmex service) {
        this.service = service;
    }

    @Override
    public Observable<OpenOrders> getOpenOrdersObservable(Object... args) {
        return service.subscribeChannel("order", "order")
                .map(BitmexAdapters::adaptOpenOrdersUpdate);
    }
}
