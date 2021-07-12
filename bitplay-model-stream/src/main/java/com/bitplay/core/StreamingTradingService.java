package com.bitplay.core;

import com.bitplay.service.exception.NotAuthorizedException;
import com.bitplay.service.exception.NotConnectedException;
import com.bitplay.xchange.dto.trade.OpenOrders;
import io.reactivex.Observable;

public interface StreamingTradingService {

    /**
     * Get an order book representing the current offered exchange rates (market depth). Emits {@link NotConnectedException}
     * When not connected to the WebSocket API. Emits {@link NotAuthorizedException} When not authorized.
     *
     * @return {@link Observable} that emits {@link OpenOrders} when exchange sends the update.
     */
    Observable<OpenOrders> getOpenOrdersObservable();

    /**
     * Get one order information.
     *
     * @param args additional parameters like orderId
     * @return {@link Observable} that emits {@link OpenOrders} when exchange sends the update.
     */
    Observable<OpenOrders> getOpenOrderObservable(Object... args);
}
