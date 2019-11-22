package com.bitplay.okex.v3.service.futures;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.BaseTests;
import com.bitplay.okex.v3.client.ApiCredentials;
import com.bitplay.okex.v3.dto.futures.param.ClosePosition;
import com.bitplay.okex.v3.dto.futures.result.SwapAccounts;
import com.bitplay.okex.v3.enums.FuturesDirectionEnum;
import com.bitplay.okex.v3.service.futures.api.FuturesTradeApiService;
import com.bitplay.okex.v3.service.swap.SwapPrivateApi;
import com.bitplay.okex.v3.service.swap.api.SwapTradeApiServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwapTradeApiServiceTest extends BaseTests {

    private static final Logger LOG = LoggerFactory.getLogger(SwapTradeApiServiceTest.class);

    private SwapPrivateApi tradeApiService;

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
        tradeApiService = new SwapPrivateApi(config);
    }

    @Test
    public void getAccounts() throws JsonProcessingException {
//        final SwapAccounts accounts = tradeApiService.getAccountsByInstrumentId("ETH-USD-SWAP");
//        toResultString(LOG, "depth", accounts.getInfo());
//        final Object accounts = ((SwapTradeApiServiceImpl) tradeApiService).testAccount("ETH-USD-SWAP");
//        toResultString(LOG, "depth", accounts);
//        LOG.info(accounts.toString());
    }

    @Test
    public void getPosition() throws JsonProcessingException {
//        final OkexAllPositions positions = tradeApiService.getPositions();
        final Object positions = tradeApiService.testPositions();
        System.out.println(positions);
        toResultString(LOG, "pos", positions);

        final ObjectMapper mapper = new ObjectMapper();
//        final OkexPosition pos = byInstrumentId.get();
        final String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(positions);
//        LOG.info(positions.toString());
        LOG.info(s);
//        LOG.info(pos.getLongPnl().toPlainString());
//        LOG.info(pos.getLongUnrealisedPnl().toPlainString());
        // "instrument_id" : "BTC-USD-190920",

//        LOG.info(positions.getResult());
//        LOG.info(positions.getHolding().toString());
    }

    @Test
    public void getInstrumentPosition() throws JsonProcessingException {
        final Object p = tradeApiService.testInstrumentPosition("ETH-USD-SWAP");

        final ObjectMapper mapper = new ObjectMapper();
        final String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(p);
        LOG.info(s);
    }

//    @Test
    public void getOrders() throws JsonProcessingException {
//        final Object p = tradeApiService.getOpenOrders("BTC-USD-190920");
//        final ObjectMapper mapper = new ObjectMapper();
//        final String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(p);
//        LOG.info(s);
    }

//    @Test
    public void getOrder() throws JsonProcessingException {
//        final Object p = tradeApiService.getOrder("BTC-USD-190920", "35539403969710089");
//        final ObjectMapper mapper = new ObjectMapper();
//        final String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(p);
//        LOG.info(s);
    }

//    @Test
    public void cancellOrder() throws JsonProcessingException {
//        final Object p = tradeApiService.getOpenOrders("BTC-USD-190927");
//        final Object p = tradeApiService.cancelOrder("BTC-USD-190927", "3554909671083008");
//        final ObjectMapper mapper = new ObjectMapper();
//        final String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(p);
//        LOG.info(s);
    }

    //    @Test
    public void closePosition() throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        final ClosePosition closePosition = new ClosePosition("BTC-USD-190927", FuturesDirectionEnum.LONG);
        LOG.info(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(closePosition));

//        final Object p = tradeApiService.closePosition(closePosition);
//        final String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(p);
//        LOG.info(s);

        // if pos == 0
        // com.bitplay.okex.v3.exception.ApiException:
        // 32014 : Positions that you are squaring exceeded the total no. of contracts allowed to close
    }

}
