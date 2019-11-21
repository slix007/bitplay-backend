package com.bitplay.okex.v3.service.futures;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.BaseTests;
import com.bitplay.okex.v3.client.ApiCredentials;
import com.bitplay.okex.v3.dto.futures.result.Book;
import com.bitplay.okex.v3.dto.futures.result.Instrument;
import com.bitplay.okex.v3.service.futures.impl.FuturesMarketApiService;
import com.bitplay.okex.v3.service.swap.impl.SwapMarketApiServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SwapMarketApiServiceTest extends BaseTests {

    private static final Logger LOG = LoggerFactory.getLogger(FuturesMarketApiService.class);

    private FuturesMarketApiService marketAPIService;

    public ApiConfiguration config() {
        ApiConfiguration config = new ApiConfiguration();

        config.setEndpoint(ApiConfiguration.API_BASE_URL);
        final ApiCredentials cred = new ApiCredentials();
        cred.setApiKey("");
        cred.setSecretKey("");
        cred.setPassphrase("");
        config.setApiCredentials(cred);
        config.setPrint(true);
        return config;
    }

    @Before
    public void setUp() {
        config = config();
        marketAPIService = new SwapMarketApiServiceImpl(config);
    }

    @Test
    public void testGetInstruments() throws JsonProcessingException {
        List<Instrument> instruments = marketAPIService.getInstruments();
        toResultString(LOG, "Instruments", instruments);
    }

    @Test
    public void testOrderBook() throws JsonProcessingException {
        final Book res = marketAPIService.getInstrumentBook("BTC-USD-SWAP");
        toResultString(LOG, "depth", res);
//        System.out.println(res);
    }
}
