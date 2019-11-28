package com.bitplay.okex.v3.service.futures;

import com.bitplay.model.EstimatedPrice;
import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.dto.futures.result.Book;
import com.bitplay.okex.v3.service.futures.adapter.BookAdapter;
import com.bitplay.okex.v3.service.futures.api.FuturesMarketApiServiceImpl;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

import java.math.BigDecimal;

public class FuturesPublicApi extends FuturesMarketApiServiceImpl {

    public FuturesPublicApi(ApiConfiguration config) {
        super(config);
    }

    @Override
    public OrderBook getInstrumentBook(String instrumentId, CurrencyPair currencyPair) {
        final Book book = getInstrumentBookApi(instrumentId);
        return BookAdapter.convertBook(book, currencyPair);
    }

    @Override
    public EstimatedPrice getEstimatedPrice(String instrumentId) {
        final com.bitplay.okex.v3.dto.futures.result.EstimatedPrice r = getEstimatedPriceApi(instrumentId);
        final BigDecimal price = r.getSettlement_price() != null ? r.getSettlement_price() : BigDecimal.ZERO;
        return new EstimatedPrice(price, r.getTimestamp());
    }


}
