package info.bitrich.xchangestream.bitmex;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingAccountService;
import io.reactivex.Observable;
import io.swagger.client.model.Margin;
import io.swagger.client.model.Position;
import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;

public class BitmexStreamingAccountService implements StreamingAccountService {

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

                    Margin margin = mapper.treeToValue(s.get("data").get(0), Margin.class);

                    return BitmexAdapters.adaptBitmexMargin(margin);
                }).share();
    }

    public Observable<org.knowm.xchange.dto.account.Position> getPositionObservable(String symbol) {
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
