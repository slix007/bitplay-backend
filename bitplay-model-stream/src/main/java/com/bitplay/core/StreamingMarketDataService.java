package com.bitplay.core;

import com.bitplay.service.exception.NotConnectedException;
import io.reactivex.Observable;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.marketdata.Ticker;
import com.bitplay.xchange.dto.marketdata.Trade;


public interface StreamingMarketDataService {
    /**
     * Get an order book representing the current offered exchange rates (market depth).
     * Emits {@link NotConnectedException} When not connected to the WebSocket API.
     *
     * @param currencyPair Currency pair of the order book
     * @return {@link Observable} that emits {@link OrderBook} when exchange sends the update.
     */
    Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args);

    /**
     * Get a ticker representing the current exchange rate.
     * Emits {@link NotConnectedException} When not connected to the WebSocket API.
     *
     * @param currencyPair Currency pair of the ticker
     * @return {@link Observable} that emits {@link Ticker} when exchange sends the update.
     */
    Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args);

    /**
     * Get the trades performed by the exchange.
     * Emits {@link NotConnectedException} When not connected to the WebSocket API.
     *
     * @param currencyPair Currency pair of the trades
     * @return {@link Observable} that emits {@link Trade} when exchange sends the update.
     */
    Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args);
}
