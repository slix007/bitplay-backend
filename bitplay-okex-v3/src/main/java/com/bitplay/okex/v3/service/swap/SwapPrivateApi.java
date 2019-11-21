package com.bitplay.okex.v3.service.swap;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.service.swap.impl.SwapTradeApiServiceImpl;

public class SwapPrivateApi extends SwapTradeApiServiceImpl {

    public SwapPrivateApi(ApiConfiguration config) {
        super(config);
    }
}
