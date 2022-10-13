package com.bitplay.okexv5;

import com.bitplay.core.ProductSubscription;
import com.bitplay.core.StreamingAccountService;
import com.bitplay.core.StreamingExchangeEx;
import com.bitplay.core.StreamingMarketDataService;
import com.bitplay.core.StreamingPrivateDataService;
import com.bitplay.core.StreamingTradingService;
import com.bitplay.service.ws.WsConnectableService;
import com.bitplay.service.ws.WsConnectionSpec;
import com.bitplay.service.ws.statistic.PingStatEvent;
import com.bitplay.xchange.ExchangeSpecification;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.meta.ExchangeMetaData;
import com.bitplay.xchange.exceptions.ExchangeException;
import com.bitplay.xchange.exceptions.NotYetImplementedForExchangeException;
import com.bitplay.xchange.service.account.AccountService;
import com.bitplay.xchange.service.marketdata.MarketDataService;
import com.bitplay.xchange.service.trade.TradeService;
import io.reactivex.Completable;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.List;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Created by Sergei Shurmin on 02.03.19.
 */
public class OkExStreamingExchangeV5 implements StreamingExchangeEx {

    protected static final String PUBLIC_API_URI = "wss://ws.okx.com:8443/ws/v5/public";
    protected static final String PRIVATE_API_URI = "wss://ws.okx.com:8443/ws/v5/private";


    private OkExStreamingPrivateDataServiceV5 streamingPrivateDataService;
    private OkExStreamingMarketDataService streamingMarketDataService;

    protected OkexStreamingServiceWsToRxV5 streamingService;

    protected ExchangeSpecification exchangeSpecification;


    protected void initServices(ProductSubscription productSubscription) {
        // init Streaming
        streamingService = createStreamingService(productSubscription == ProductSubscription.PUBLIC
                ? PUBLIC_API_URI : PRIVATE_API_URI);
        streamingMarketDataService = new OkExStreamingMarketDataService(streamingService);
        streamingPrivateDataService = new OkExStreamingPrivateDataServiceV5(streamingService, this);
    }

    protected OkexStreamingServiceWsToRxV5 createStreamingService(String apiUrl) {
        OkexStreamingServiceWsToRxV5 streamingService = new OkexStreamingServiceWsToRxV5(apiUrl);
        applyStreamingSpecification(getExchangeSpecification(), streamingService);
//        if (StringUtils.isNotEmpty(exchangeSpecification.getApiKey())) {
//            streamingService.setApiKey(exchangeSpecification.getApiKey());
//            streamingService.setApiSecret(exchangeSpecification.getSecretKey());
//        }
        return streamingService;
    }


    @Override
    public StreamingMarketDataService getStreamingMarketDataService() {
        return streamingMarketDataService;
    }

    @Override
    public StreamingPrivateDataService getStreamingPrivateDataService() {
        return streamingPrivateDataService;
    }

    @Override
    public StreamingAccountService getStreamingAccountService() {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public StreamingTradingService getStreamingTradingService() {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public Completable connect(ProductSubscription... args) {
        return streamingService.connect();
    }

    @Override
    public Completable disconnect() {
        return streamingService.disconnect();
    }

    @Override
    public boolean isAlive() {
        return streamingService.isSocketOpen();
    }

    @Override
    public void useCompressedMessages(boolean compressedMessages) {

    }


    @Override
    public Completable onDisconnect() {
        return streamingService.onDisconnect();
    }

    @Override
    public ExchangeSpecification getExchangeSpecification() {
        return exchangeSpecification;
    }

    @Override
    public ExchangeMetaData getExchangeMetaData() {
        return null;
    }

    @Override
    public List<CurrencyPair> getExchangeSymbols() {
        return null;
    }

    @Override
    public SynchronizedValueFactory<Long> getNonceFactory() {
        return null;
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        return null;
    }

    @Override
    public void applyStreamingSpecification(ExchangeSpecification exchangeSpec, WsConnectableService streamingService) {
        final WsConnectionSpec wsSpec = streamingService.getWsConnectionSpec();
        final Object socksProxyHost = exchangeSpec.getExchangeSpecificParametersItem(SOCKS_PROXY_HOST);
        if (socksProxyHost != null) {
            wsSpec.setSocksProxyHost((String) socksProxyHost);
        }
        final Object socksProxyPort = exchangeSpec.getExchangeSpecificParametersItem(SOCKS_PROXY_PORT);
        if (socksProxyPort != null) {
            wsSpec.setSocksProxyPort((Integer) socksProxyPort);
        }
//        wsSpec.setBeforeConnectionHandler((Runnable) exchangeSpec.getExchangeSpecificParametersItem(ConnectableService.BEFORE_CONNECTION_HANDLER));

        final Boolean accept_all_ceriticates = (Boolean) exchangeSpec.getExchangeSpecificParametersItem(ACCEPT_ALL_CERITICATES);
        if (accept_all_ceriticates != null && accept_all_ceriticates) {
            wsSpec.setAcceptAllCertificates(true);
        }

        final Boolean enable_logging_handler = (Boolean) exchangeSpec.getExchangeSpecificParametersItem(ENABLE_LOGGING_HANDLER);
        if (enable_logging_handler != null && enable_logging_handler) {
            wsSpec.setEnableLoggingHandler(true);
        }

    }

    @Override
    public void applySpecification(ExchangeSpecification exchangeSpecification) {
        //// from BaseExchange
        {
            // getDefaultExchangeSpecification();
            exchangeSpecification.setSslUri("https://www.okcoin.com/api");
            exchangeSpecification.setHost("www.okcoin.com");
            exchangeSpecification.setExchangeName("OKCoin");
            exchangeSpecification.setExchangeDescription("OKCoin is a globally oriented crypto-currency trading platform.");

            // set to true to automatically use the Intl_ parameters for ssluri and host
            exchangeSpecification.setExchangeSpecificParametersItem("Use_Intl", false);
            exchangeSpecification.setExchangeSpecificParametersItem("Use_Futures", false);

            exchangeSpecification.setExchangeSpecificParametersItem("Websocket_SslUri", "wss://real.okcoin.cn:10440/websocket/okcoinapi");
            this.exchangeSpecification = exchangeSpecification;
        }

        final ProductSubscription productSubscription =
                (ProductSubscription) exchangeSpecification.getExchangeSpecificParametersItem("productSubscription");

        initServices(productSubscription);

        //// from OkCoinExchange
//        if (exchangeSpecification.getExchangeSpecificParameters() != null) {
//            if (exchangeSpecification.getExchangeSpecificParametersItem("Use_Futures").equals(true)) {
//                exchangeSpecification.setSslUri("https://www.okex.com/api");
//                exchangeSpecification.setHost("www.okex.com");
//                exchangeSpecification.setExchangeSpecificParametersItem("Websocket_SslUri", "wss://real.okex.com:10440/websocket/okcoinapi");
//            } else if (exchangeSpecification.getExchangeSpecificParametersItem("Use_Intl").equals(true)) {
//                exchangeSpecification.setSslUri("https://www.okcoin.com/api");
//                exchangeSpecification.setHost("www.okcoin.com");
//                exchangeSpecification.setExchangeSpecificParametersItem("Websocket_SslUri", "wss://real.okcoin.com:10440/websocket/okcoinapi");
//            }
//        }

    }

    @Override
    public MarketDataService getMarketDataService() {
        return null;
    }

    @Override
    public TradeService getTradeService() {
        return null;
    }

    @Override
    public AccountService getAccountService() {
        return null;
    }

    @Override
    public void remoteInit() throws IOException, ExchangeException {

    }

    public Observable<PingStatEvent> subscribePingStats() {
        return streamingService.subscribePingStats();

    }

}
