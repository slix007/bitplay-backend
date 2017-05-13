package org.knowm.xchange.bitmex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitmex.BitmexAuthenitcatedApi;
import org.knowm.xchange.bitmex.BitmexPublicApi;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

import si.mazi.rescu.ClientConfig;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestProxyFactory;
import si.mazi.rescu.serialization.jackson.DefaultJacksonObjectMapperFactory;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexBaseService extends BaseExchangeService implements BaseService {

    protected final BitmexAuthenitcatedApi bitmexAuthenitcatedApi;
    protected final BitmexPublicApi bitmexPublicApi;
    protected ParamsDigest signatureCreator;

    /**
     * Constructor
     */
    protected BitmexBaseService(Exchange exchange) {
        super(exchange);

        ClientConfig config = new ClientConfig();
        config.setJacksonObjectMapperFactory(new DefaultJacksonObjectMapperFactory() {
            @Override
            public void configureObjectMapper(ObjectMapper objectMapper) {
                super.configureObjectMapper(objectMapper);

//                objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
                objectMapper.registerModule(new JavaTimeModule());
            }
        });

        bitmexAuthenitcatedApi = RestProxyFactory.createProxy(BitmexAuthenitcatedApi.class,
                exchange.getExchangeSpecification().getSslUri(), config);

        bitmexPublicApi = RestProxyFactory.createProxy(BitmexPublicApi.class,
                exchange.getExchangeSpecification().getSslUri(), config);

        signatureCreator = BitmexDigest.createInstance(
                exchange.getExchangeSpecification().getApiKey(),
                exchange.getExchangeSpecification().getSecretKey());



    }
}
