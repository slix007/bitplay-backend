package com.bitplay.okex.v3.service.futures;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.service.futures.impl.FuturesTradeApiServiceImpl;

public class FuturesPrivateApi extends FuturesTradeApiServiceImpl {

    public FuturesPrivateApi(ApiConfiguration config) {
        super(config);
    }
}
