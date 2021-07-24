package com.bitplay.okex.v5;

import com.bitplay.externalapi.FplayExchange;
import com.bitplay.externalapi.PrivateApi;
import com.bitplay.externalapi.PublicApi;
import com.bitplay.okex.v5.service.PublicApiV5;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class FplayExchangeOkexV5 extends FplayExchange {

    public FplayExchangeOkexV5(PublicApi publicApi, PrivateApi privateApi) {
        super(publicApi, privateApi);
    }

    public static FplayExchangeOkexV5 create(ApiConfiguration config, String futuresContractName) {
        final PublicApi publicApi = new PublicApiV5(config);
        final PrivateApi privateApi = null;
//        if (futuresContractName.equals("swap")) {
//            publicApi = new SwapPublicApi(config);
//            privateApi = new SwapPrivateApi(config);
//        } else {
//            publicApi = new FuturesPublicApi(config);
//            privateApi = new FuturesPrivateApi(config);
//        }
        return new FplayExchangeOkexV5(publicApi, privateApi);
    }

}
