package com.bitplay.okex.v3.service.swap.impl;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.client.ApiClient;
import com.bitplay.okex.v3.dto.futures.result.Book;
import com.bitplay.okex.v3.dto.futures.result.EstimatedPrice;
import com.bitplay.okex.v3.dto.futures.result.Instrument;

import java.util.List;

public abstract class SwapMarketApiServiceImpl implements SwapMarketApiService {


    private ApiClient client;
    private SwapMarketApi api;

    public SwapMarketApiServiceImpl(ApiConfiguration config) {
        this.client = new ApiClient(config);
        this.api = client.createService(SwapMarketApi.class);
    }

    @Override
    public List<Instrument> getInstruments() {
        return this.client.executeSync(this.api.getInstruments());
    }

    @Override
    public EstimatedPrice getEstimatedPrice(String instrumentId) {
        return  null;
    }

    @Override
    public Book getInstrumentBook(String instrumentId) {
        return this.client.executeSync(this.api.getInstrumentBook(instrumentId, 20));
    }
}
