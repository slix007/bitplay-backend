package com.bitplay.okex.v3.service.futures;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.BaseTests;
import com.bitplay.okex.v3.client.ApiCredentials;
import com.bitplay.okex.v3.dto.futures.result.Accounts;
import com.bitplay.okex.v3.service.futures.impl.FuturesTradeApiServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FuturesTradeApiServiceTest extends BaseTests {

    private static final Logger LOG = LoggerFactory.getLogger(FuturesTradeApiServiceTest.class);

    private FuturesTradeApiService tradeApiService;

    private static final String apiKey = "68d6396d-7f11-40a1-8564-12fb6da81134";
    private static final String secretKey = "92CEFAB9CC9F9F1E771C7B26904546FE";
    private static final String passphrase = "LocalTestKey";


    public ApiConfiguration config() {
        ApiConfiguration config = new ApiConfiguration();

        config.setEndpoint(ApiConfiguration.API_BASE_URL);
        final ApiCredentials cred = new ApiCredentials();
        cred.setApiKey(apiKey);
        cred.setSecretKey(secretKey);
        cred.setPassphrase(passphrase);
        config.setApiCredentials(cred);
        config.setPrint(true);
        return config;
    }

    @Before
    public void setUp() {
        config = config();
        tradeApiService = new FuturesTradeApiServiceImpl(config);
    }

    @Test
    public void getAccounts() {
        final Accounts accounts = tradeApiService.getAccounts();
        LOG.info(accounts.toString());
    }
}