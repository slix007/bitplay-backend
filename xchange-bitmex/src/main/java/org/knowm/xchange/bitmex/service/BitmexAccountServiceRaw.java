package org.knowm.xchange.bitmex.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.account.Balance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.swagger.client.ApiException;
import io.swagger.client.model.Margin;
import io.swagger.client.model.Wallet;

import static org.knowm.xchange.bitmex.BitmexAdapters.adaptBitmexBalance;
import static org.knowm.xchange.bitmex.BitmexAdapters.adaptBitmexMargin;

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
        String xbt = "XBt";

        List<Balance> balances = new ArrayList<Balance>();
        final Wallet xbtWallet = bitmexAuthenitcatedApi.wallet(exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(), xbt);
        balances.add(adaptBitmexBalance(xbtWallet));

        final Margin margin = bitmexAuthenitcatedApi.margin(exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(), xbt);
        balances.add(adaptBitmexMargin(margin));

        return balances;
    }

}
