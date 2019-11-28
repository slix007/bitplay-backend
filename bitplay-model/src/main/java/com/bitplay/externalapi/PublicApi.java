package com.bitplay.externalapi;

import com.bitplay.model.EstimatedPrice;
import com.bitplay.model.SwapSettlement;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

public interface PublicApi {

//    Ticker getTicker(CurrencyPair currencyPair, Object... args)
//            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException;
//
//    OrderBook getOrderBook(CurrencyPair currencyPair, Object... args)
//            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException;

    OrderBook getInstrumentBook(String instrumentId, CurrencyPair currencyPair);


    EstimatedPrice getEstimatedPrice(String instrumentId);

    default SwapSettlement getSwapSettlement(String instrumentId) {
        throw new UnsupportedOperationException();
    }
}
