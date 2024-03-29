package com.bitplay.core;

import com.bitplay.service.ConnectableService;
import com.bitplay.service.netty.NettyStreamingService;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.ExchangeSpecification;
import com.bitplay.xchange.exceptions.NotYetImplementedForExchangeException;
import io.reactivex.Completable;
import io.reactivex.Observable;

public interface StreamingExchange extends Exchange {
    String USE_SANDBOX = "Use_Sandbox";
    String ACCEPT_ALL_CERITICATES = "Accept_All_Ceriticates";
    String ENABLE_LOGGING_HANDLER = "Enable_Logging_Handler";
    String SOCKS_PROXY_HOST = "SOCKS_Proxy_Host";
    String SOCKS_PROXY_PORT = "SOCKS_Proxy_Port";

    String API_URL = "Api_Url";

    /**
     * Connects to the WebSocket API of the exchange.
     *
     * @return {@link Completable} that completes upon successful connection.
     */
    Completable connect();

    /**
     * Disconnect from the WebSocket API.
     *
     * @return {@link Completable} that completes upon successful disconnect.
     */
    Completable disconnect();

    /**
     * Checks whether connection to the exchange is alive.
     *
     * @return true if connection is open, otherwise false.
     */
    boolean isAlive();

    /**
     * Observable for reconnection failure event.
     * When this happens, it usually indicates that the server or the network is down.
     *
     * @return Observable with the exception during reconnection.
     */
    default Observable<Throwable> reconnectFailure() {
        throw new NotYetImplementedForExchangeException();
    }

    /**
     * Observable for connection success event.
     * When this happens, it usually indicates that the server or the network is down.
     *
     * @return Observable with the exception during reconnection.
     */
    default Observable<Object> connectionSuccess() {
        throw new NotYetImplementedForExchangeException();
    }

    /**
     * Returns service that can be used to access market data.
     */
    StreamingMarketDataService getStreamingMarketDataService();

    /**
     * Set whether or not to enable compression handler.
     *
     * @param compressedMessages Defaults to false
     */
    void useCompressedMessages(boolean compressedMessages);

    default void applyStreamingSpecification(ExchangeSpecification exchangeSpec, NettyStreamingService streamingService){
        streamingService.setSocksProxyHost((String) exchangeSpec.getExchangeSpecificParametersItem(SOCKS_PROXY_HOST));
        streamingService.setSocksProxyPort((Integer) exchangeSpec.getExchangeSpecificParametersItem(SOCKS_PROXY_PORT));
        streamingService.setBeforeConnectionHandler((Runnable) exchangeSpec.getExchangeSpecificParametersItem(ConnectableService.BEFORE_CONNECTION_HANDLER));

        Boolean accept_all_ceriticates = (Boolean) exchangeSpec.getExchangeSpecificParametersItem(ACCEPT_ALL_CERITICATES);
        if (accept_all_ceriticates != null && accept_all_ceriticates) {
            streamingService.setAcceptAllCertificates(true);
        }

        Boolean enable_logging_handler = (Boolean) exchangeSpec.getExchangeSpecificParametersItem(ENABLE_LOGGING_HANDLER);
        if (enable_logging_handler != null && enable_logging_handler) {
            streamingService.setEnableLoggingHandler(true);
        }
    }

}
