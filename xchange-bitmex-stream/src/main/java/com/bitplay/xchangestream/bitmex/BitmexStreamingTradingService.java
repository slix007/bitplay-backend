package com.bitplay.xchangestream.bitmex;

import com.bitplay.core.StreamingTradingService;
import com.bitplay.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import io.reactivex.Observable;
import java.util.Map;
import com.bitplay.xchange.bitmex.BitmexAdapters;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.trade.OpenOrders;
import com.bitplay.xchange.exceptions.NotYetImplementedForExchangeException;

/**
 * Created by Sergey Shurmin on 5/17/17.
 */
public class BitmexStreamingTradingService implements StreamingTradingService {

    private final StreamingServiceBitmex service;

    public BitmexStreamingTradingService(StreamingServiceBitmex service) {
        this.service = service;
    }

    @Override
    public Observable<OpenOrders> getOpenOrdersObservable() {
        throw new NotYetImplementedForExchangeException();
//        return service.subscribeChannel("order", "order")
//                .map(BitmexAdapters::adaptOpenOrdersUpdate);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Observable<OpenOrders> getOpenOrderObservable(Object... objects) {
        Map<CurrencyPair, Integer> currencyToScale = (Map<CurrencyPair, Integer>) objects[0];

        // subcribe to "order" or "order:XBTUSD" or "order:ETHUSD"
//        String channel = "order:" + symbol;
        return service.subscribeChannel("order", "order")
                .map(jsonNode ->
                        BitmexAdapters.adaptOpenOrdersUpdate(jsonNode, currencyToScale)
                );
    }
}
