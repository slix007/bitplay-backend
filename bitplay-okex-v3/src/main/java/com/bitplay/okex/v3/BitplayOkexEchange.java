package com.bitplay.okex.v3;

import com.bitplay.okex.v3.service.futures.FuturesMarketApiService;
import com.bitplay.okex.v3.service.futures.FuturesTradeApiService;
import com.bitplay.okex.v3.service.futures.impl.FuturesMarketApiServiceImpl;
import com.bitplay.okex.v3.service.futures.impl.FuturesTradeApiServiceImpl;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class BitplayOkexEchange {

    private FuturesMarketApiService marketAPIService;
    private FuturesTradeApiService tradeApiService;

    public BitplayOkexEchange(ApiConfiguration config) {
        marketAPIService = new FuturesMarketApiServiceImpl(config);
        tradeApiService = new FuturesTradeApiServiceImpl(config);
    }

}
