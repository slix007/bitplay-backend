package com.bitplay.okex.v3.service.futures;

import com.bitplay.okex.v3.dto.futures.result.Instrument;
import java.util.List;


public interface FuturesMarketApiService {

    /**
     * Get all of futures contract list
     */
    List<Instrument> getInstruments();
}
