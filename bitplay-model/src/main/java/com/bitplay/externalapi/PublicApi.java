package com.bitplay.externalapi;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import java.io.IOException;

public interface PublicApi {

    Ticker getTicker(CurrencyPair currencyPair, Object... args)
            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException;

    OrderBook getOrderBook(CurrencyPair currencyPair, Object... args)
            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException;

}
