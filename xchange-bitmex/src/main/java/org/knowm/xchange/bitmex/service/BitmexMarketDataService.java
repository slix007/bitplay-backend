package org.knowm.xchange.bitmex.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.marketdata.MarketDataService;

import java.io.IOException;
import java.util.List;

import io.swagger.client.ApiException;
import io.swagger.client.model.OrderBookL2;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexMarketDataService extends BitmexMarketDataServiceRaw implements MarketDataService {

    /**
     * Constructor
     */
    public BitmexMarketDataService(Exchange exchange) {
        super(exchange);
    }

    public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return null;
    }

    public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String symbol = currencyPair.base.getCurrencyCode();
        Integer depth = 25;
        if (args != null && args.length > 0) {
            if (args[0] instanceof Integer) {
                depth = (Integer) args[0];
            } else {
                throw new ExchangeException("Orderbook size argument must be an Integer!");
            }
        }

        final List<OrderBookL2> bitmexMarketDepth;
        try {
            bitmexMarketDepth = getBitmexMarketDepth(symbol, depth);
        } catch (ApiException e) {
            throw new ExchangeException("Can not get such a symbol depth", e);
        }
        return BitmexAdapters.adaptBitmexOrderBook(bitmexMarketDepth, currencyPair);
    }

    public Trades getTrades(CurrencyPair currencyPair, Object... args) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return null;
    }
}
