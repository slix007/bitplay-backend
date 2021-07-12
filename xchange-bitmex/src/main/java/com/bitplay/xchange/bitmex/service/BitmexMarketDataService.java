package com.bitplay.xchange.bitmex.service;

import com.bitplay.xchange.bitmex.BitmexAdapters;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.marketdata.Ticker;
import com.bitplay.xchange.dto.marketdata.Trades;
import com.bitplay.xchange.exceptions.ExchangeException;
import com.bitplay.xchange.exceptions.NotAvailableFromExchangeException;
import com.bitplay.xchange.exceptions.NotYetImplementedForExchangeException;
import com.bitplay.xchange.service.marketdata.MarketDataService;

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
//        final String symbol = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("Symbol");
        final Integer scale = (Integer) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("Scale");
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
        return BitmexAdapters.adaptBitmexOrderBook(bitmexMarketDepth, currencyPair, scale);
    }

    public Trades getTrades(CurrencyPair currencyPair, Object... args) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return null;
    }
}
