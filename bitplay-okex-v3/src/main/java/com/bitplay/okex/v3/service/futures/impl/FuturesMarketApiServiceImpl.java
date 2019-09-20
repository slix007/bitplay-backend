package com.bitplay.okex.v3.service.futures.impl;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.client.ApiClient;
import com.bitplay.okex.v3.dto.futures.result.Book;
import com.bitplay.okex.v3.dto.futures.result.EstimatedPrice;
import com.bitplay.okex.v3.dto.futures.result.Instrument;
import com.bitplay.okex.v3.service.futures.FuturesMarketApiService;
import java.util.List;

public class FuturesMarketApiServiceImpl implements FuturesMarketApiService {


    private ApiClient client;
    private FuturesMarketApi api;

    public FuturesMarketApiServiceImpl(ApiConfiguration config) {
        this.client = new ApiClient(config);
        this.api = client.createService(FuturesMarketApi.class);
    }

    @Override
    public List<Instrument> getInstruments() {
        return this.client.executeSync(this.api.getInstruments());
    }

    @Override
    public EstimatedPrice getEstimatedPrice(String instrumentId) {
        return this.client.executeSync(this.api.getInstrumentEstimatedPrice(instrumentId));
    }

    @Override
    public Book getInstrumentBook(String instrumentId) {
        return this.client.executeSync(this.api.getInstrumentBook(instrumentId, 20));
    }
}
