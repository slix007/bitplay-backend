package com.bitplay.okex.v3.service.futures;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.BaseTests;
import com.bitplay.okex.v3.client.ApiCredentials;
import com.bitplay.okex.v3.dto.futures.result.Instrument;
import com.bitplay.okex.v3.service.futures.impl.FuturesMarketApiServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FuturesMarketApiServiceTest extends BaseTests {

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
        marketAPIService = new FuturesMarketApiServiceImpl(config);
    }

    @Test
    public void testGetInstruments() throws JsonProcessingException {
        List<Instrument> instruments = marketAPIService.getInstruments();
        toResultString(LOG, "Instruments", instruments);
    }
}