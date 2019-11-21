package com.bitplay.okex.v3.service.futures.impl;

import com.bitplay.externalapi.PrivateApi;
import com.bitplay.externalapi.PublicApi;
import com.bitplay.okex.v3.dto.futures.result.Book;
import com.bitplay.okex.v3.dto.futures.result.EstimatedPrice;
import com.bitplay.okex.v3.dto.futures.result.Instrument;
import java.util.List;


public interface FuturesMarketApiService extends PublicApi {

    /**
     * Get all of futures contract list
     */
    List<Instrument> getInstruments();

    EstimatedPrice getEstimatedPrice(String instrumentId);
    Book getInstrumentBook(String instrumentId);
}
