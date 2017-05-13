package org.knowm.xchange.bitmex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.swagger.client.ApiException;
import io.swagger.client.model.Wallet;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexAccountServiceRaw extends BitmexBaseService {
    /**
     * Constructor
     */
    protected BitmexAccountServiceRaw(Exchange exchange) {
        super(exchange);
    }

    public List<Balance> getWallets() throws ApiException, IOException {
        String xbt = "XBT";
        String usd = "USD";

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());


        List<Balance> balances = new ArrayList<Balance>();
        final Wallet wallet = bitmexAuthenitcatedApi.wallet(
                exchange.getExchangeSpecification().getApiKey(),
                signatureCreator,
                exchange.getNonceFactory());

        System.out.println(wallet);

        balances.add(adaptBitmexBalance(wallet));

//        final Wallet xbtWallet = bitmexAuthenticated.getWallet(xbt);
//        final Wallet usdWallet = bitmexAuthenticated.getWallet(usd);
//        balances.add(adaptBitmexBalance(xbtWallet));
//        balances.add(adaptBitmexBalance(usdWallet));

        return balances;
    }

    private Balance adaptBitmexBalance(Wallet wallet) {
        return new Balance(new Currency(wallet.getCurrency()), wallet.getAmount(), wallet.getAmount());
    }
}
