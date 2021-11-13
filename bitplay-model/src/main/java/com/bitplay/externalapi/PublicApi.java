package com.bitplay.externalapi;

import com.bitplay.model.EstimatedPrice;
import com.bitplay.model.SwapSettlement;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import java.util.List;

public interface PublicApi {

//    Ticker getTicker(CurrencyPair currencyPair, Object... args)
//            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException;
//
//    default OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws Exception {
//        throw new NotYetImplementedForExchangeException();
//    }

    OrderBook getInstrumentBook(String instrumentId, CurrencyPair currencyPair);


    EstimatedPrice getEstimatedPrice(String instrumentId);

    default SwapSettlement getSwapSettlement(String instrumentId) {
        throw new UnsupportedOperationException();
    }

    List<String> getAvailableInstruments();
}
