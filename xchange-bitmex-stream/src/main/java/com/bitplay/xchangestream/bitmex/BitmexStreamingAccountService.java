package com.bitplay.xchangestream.bitmex;

import com.bitplay.model.Pos;
import com.bitplay.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.bitplay.core.StreamingAccountService;
import io.reactivex.Observable;
import io.swagger.client.model.Margin;
import io.swagger.client.model.Position;
import com.bitplay.xchange.bitmex.BitmexAdapters;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.account.AccountInfo;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.exceptions.NotAvailableFromExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BitmexStreamingAccountService implements StreamingAccountService {

    private static final Logger logger = LoggerFactory.getLogger(BitmexStreamingAccountService.class);

    private final StreamingServiceBitmex service;

    BitmexStreamingAccountService(StreamingServiceBitmex service) {
        this.service = service;
    }

    @Override
    public Observable<AccountInfo> getAccountInfoObservable(CurrencyPair currencyPair, Object... objects) {
        throw new NotAvailableFromExchangeException();
    }

    public Observable<AccountInfoContracts> getAccountInfoContractsObservable() {
        return service.subscribeChannel("margin", "margin")
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    mapper.registerModule(new JavaTimeModule());

                    final JsonNode dataNode = s.get("data");
                    for (JsonNode accountNode : dataNode) {
                        if (accountNode.get("currency").asText().equalsIgnoreCase("XBT")) {
                            Margin margin = mapper.treeToValue(accountNode, Margin.class);
                            return BitmexAdapters.adaptBitmexMargin(margin);
                        }
                    }
                    logger.warn("currency XBT not found in response " + s);
                    return new AccountInfoContracts();
//                    throw new IllegalArgumentException();
                }).share();
    }

    public Observable<Pos> getPositionObservable(String symbol) {
        return service.subscribeChannel("position", "position")
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    mapper.registerModule(new JavaTimeModule());

//                    Wallet bitmexWallet = mapper.treeToValue(s.get("data").get(0), Wallet.class);
                    Position position = null;
                    final JsonNode dataNode = s.get("data");
                    if (dataNode != null && dataNode.size() > 0) {
                        for (JsonNode posNode : dataNode) {
                            if (posNode.get("symbol") != null && posNode.get("symbol").asText().equals(symbol)) {
                                position = mapper.treeToValue(posNode, Position.class);
                                break;
                            }
                        }
                    }

                    return BitmexAdapters.adaptBitmexPosition(position, symbol);
                });
    }
}
