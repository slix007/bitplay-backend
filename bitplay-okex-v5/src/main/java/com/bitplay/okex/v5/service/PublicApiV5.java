package com.bitplay.okex.v5.service;

import com.bitplay.externalapi.PublicApi;
import com.bitplay.model.EstimatedPrice;
import com.bitplay.model.SwapSettlement;
import com.bitplay.okex.v5.ApiConfiguration;
import com.bitplay.okex.v5.client.ApiClient;
import com.bitplay.okex.v5.dto.adapter.BookAdapter;
import com.bitplay.okex.v5.dto.result.Book;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.exceptions.NotYetImplementedForExchangeException;

public class PublicApiV5 implements PublicApi {

    private ApiClient client;
    private MarketApiV5 api;

    public PublicApiV5(ApiConfiguration config) {
        this.client = new ApiClient(config);
//        this.api = client.createService(SwapMarketApi.class);
    }


    public Book getInstrumentBookApi(String instrumentId) {
        return this.client.executeSync(this.api.getInstrumentBook(instrumentId, 20));
    }

//    public SwapFundingTime getSwapFundingTime(String instrumentId) {
//        return this.client.executeSync(this.api.getSwapFundingTime(instrumentId));
//    }


    @Override
    public OrderBook getInstrumentBook(String instrumentId, CurrencyPair currencyPair) {
        final Book book = getInstrumentBookApi(instrumentId);
        return BookAdapter.convertBook(book, currencyPair);
    }

    @Override
    public EstimatedPrice getEstimatedPrice(String instrumentId) {
//        final EstimatedPrice r = getEstimatedPriceApi(instrumentId);
//        final BigDecimal price = r.getSettlement_price() != null ? r.getSettlement_price() : BigDecimal.ZERO;
//        return new EstimatedPrice(price, r.getTimestamp());
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public SwapSettlement getSwapSettlement(String instrumentId) {
        throw new UnsupportedOperationException();
    }
}
