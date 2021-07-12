package com.bitplay.market.model;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TradeResponseTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testBitmexInsufficientFunds() {
        String httpErrorCodeStr = "{\"error\":{\"message\":\"Account has insufficient Available Balance, 1110589 XBt required\",\"name\":\"ValidationError\"}}";
        final TradeResponse tradeResponse = new TradeResponse();
        tradeResponse.setErrorCode(httpErrorCodeStr);
        Assert.assertTrue(tradeResponse.errorInsufficientFunds());
    }

    @Test
    public void testOkexInsufficientFunds() {
        String httpErrorCodeStr = "32016 : Risk rate lower than 100% after opening position";
        final TradeResponse tradeResponse = new TradeResponse();
        tradeResponse.setErrorCode(httpErrorCodeStr);
        Assert.assertTrue(tradeResponse.errorInsufficientFunds());
    }
}
