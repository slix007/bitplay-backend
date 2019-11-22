package com.bitplay.okex.v3.service.futures;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.dto.futures.result.Book;
import com.bitplay.okex.v3.service.futures.adapter.BookAdapter;
import com.bitplay.okex.v3.service.futures.api.FuturesMarketApiServiceImpl;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

public class FuturesPublicApi extends FuturesMarketApiServiceImpl {

    public FuturesPublicApi(ApiConfiguration config) {
        super(config);
    }

//    @Override
//    public Ticker getTicker(CurrencyPair currencyPair, Object... args)
//            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
//        return null;
//    }
//

    @Override
    public OrderBook getInstrumentBook(String instrumentId, CurrencyPair currencyPair) {
        final Book book = getInstrumentBookApi(instrumentId);
        return BookAdapter.convertBook(book, currencyPair);
    }
}
