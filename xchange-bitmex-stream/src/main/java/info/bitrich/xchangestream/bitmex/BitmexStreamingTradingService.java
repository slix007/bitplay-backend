package info.bitrich.xchangestream.bitmex;

import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingTradingService;
import io.reactivex.Observable;
import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.dto.trade.OpenOrders;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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
        throw new NotImplementedException();
//        return service.subscribeChannel("order", "order")
//                .map(BitmexAdapters::adaptOpenOrdersUpdate);
    }

    @Override
    public Observable<OpenOrders> getOpenOrderObservable(Object... objects) {
        String symbol = (String) objects[0];
        Integer scale = (Integer) objects[1];
        String channel = "order:" + symbol;
        return service.subscribeChannel("order", channel)
                .map(jsonNode -> BitmexAdapters.adaptOpenOrdersUpdate(jsonNode, scale));
    }
}
