package com.bitplay.okex.v3;

import com.bitplay.externalapi.FplayExchange;
import com.bitplay.externalapi.PrivateApi;
import com.bitplay.externalapi.PublicApi;
import com.bitplay.okex.v3.service.futures.FuturesPrivateApi;
import com.bitplay.okex.v3.service.futures.FuturesPublicApi;
import com.bitplay.okex.v3.service.swap.SwapPrivateApi;
import com.bitplay.okex.v3.service.swap.SwapPublicApi;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class FplayExchangeOkex extends FplayExchange {

    public FplayExchangeOkex(PublicApi publicApi, PrivateApi privateApi) {
        super(publicApi, privateApi);
    }

    public static FplayExchangeOkex create(ApiConfiguration config, String futuresContractName) {
        final PublicApi publicApi;
        final PrivateApi privateApi;
        if (futuresContractName.equals("swap")) {
            publicApi = new SwapPublicApi(config);
            privateApi = new SwapPrivateApi(config);
        } else {
            publicApi = new FuturesPublicApi(config);
            privateApi = new FuturesPrivateApi(config);
        }
        return new FplayExchangeOkex(publicApi, privateApi);
    }

}
