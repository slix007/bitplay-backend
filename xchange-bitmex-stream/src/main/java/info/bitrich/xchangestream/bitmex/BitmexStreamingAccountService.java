package info.bitrich.xchangestream.bitmex;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingAccountService;

import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;

import io.reactivex.Observable;
import io.swagger.client.model.Margin;
import io.swagger.client.model.Position;

public class BitmexStreamingAccountService implements StreamingAccountService {

    private final StreamingServiceBitmex service;

    BitmexStreamingAccountService(StreamingServiceBitmex service) {
        this.service = service;
    }

    @Override
    public Observable<AccountInfo> getAccountInfoObservable(CurrencyPair currencyPair, Object... objects) {
        return service.subscribeChannel("margin", "margin")
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    mapper.registerModule(new JavaTimeModule());

                    Margin margin = mapper.treeToValue(s.get("data").get(0), Margin.class);

                    return new AccountInfo(
                            new Wallet(BitmexAdapters.adaptBitmexMargin(margin))
                    );
                }).share();
    }

    public Observable<AccountInfo> getPositionObservable() {
        return service.subscribeChannel("position", "position")
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    mapper.registerModule(new JavaTimeModule());

//                    Wallet bitmexWallet = mapper.treeToValue(s.get("data").get(0), Wallet.class);
                    Position position = mapper.treeToValue(s.get("data").get(0), Position.class);

                    final Balance balance = BitmexAdapters.adaptBitmexPosition(position);

                    return new AccountInfo(
                            new Wallet(balance)
                    );
                });
    }
}
