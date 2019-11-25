package com.bitplay.okex.v3.service.swap.api;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.client.ApiClient;
import com.bitplay.okex.v3.dto.futures.result.Book;

public abstract class SwapMarketApiServiceImpl implements SwapMarketApiService {


    private ApiClient client;
    private SwapMarketApi api;

    public SwapMarketApiServiceImpl(ApiConfiguration config) {
        this.client = new ApiClient(config);
        this.api = client.createService(SwapMarketApi.class);
    }

    public Book getInstrumentBookApi(String instrumentId) {
        return this.client.executeSync(this.api.getInstrumentBook(instrumentId, 20));
    }


//    @Override
//    public List<Instrument> getInstruments() {
//        return this.client.executeSync(this.api.getInstruments());
//    }
//
//
//    @Override
//    public Book getInstrumentBook(String instrumentId) {
//        return this.client.executeSync(this.api.getInstrumentBook(instrumentId, 20));
//    }

//    @Override
//    public Ticker getTicker(CurrencyPair currencyPair, Object... args)
//            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
//        return null;
//    }

//    @Override
//    public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args)
//            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
//        return null;
//    }
}
