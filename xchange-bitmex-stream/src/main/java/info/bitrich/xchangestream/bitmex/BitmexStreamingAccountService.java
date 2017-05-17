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

import io.reactivex.Observable;
import io.swagger.client.model.Margin;

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

//                    Wallet bitmexWallet = mapper.treeToValue(s.get("data").get(0), Wallet.class);
                    Margin margin = mapper.treeToValue(s.get("data").get(0), Margin.class);

                    final Balance balance = BitmexAdapters.adaptBitmexMargin(margin);

                    return new AccountInfo(
                            new org.knowm.xchange.dto.account.Wallet(balance)
                    );
                });
    }
}
