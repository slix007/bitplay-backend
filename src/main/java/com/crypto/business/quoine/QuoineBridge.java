package com.crypto.business.quoine;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.quoine.QuoineExchange;
import org.knowm.xchange.quoine.dto.account.FiatAccount;
import org.knowm.xchange.quoine.service.QuoineAccountServiceRaw;
import org.knowm.xchange.service.account.AccountService;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by Sergey Shurmin on 3/20/17.
 */
public class QuoineBridge extends QuoineBase {



    public void doTheWork() {
        QuoineExchange exchange = createExchange();
        // Interested in the private account functionality (authentication)
        AccountService accountService = exchange.getAccountService();

        try {
            System.out.println("GENERIC ACCOUNT");
            generic(accountService);

            System.out.println("\nSpecified account:");

            final QuoineAccountServiceRaw quoineAccountServiceRaw = (QuoineAccountServiceRaw) accountService;
            raw(quoineAccountServiceRaw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static QuoineExchange createExchange() {

        ExchangeSpecification exSpec = new ExchangeSpecification(QuoineExchange.class);

        // enter your specific API access info here
        exSpec.getExchangeSpecificParameters().put(QuoineExchange.KEY_TOKEN_ID, TOKEN_ID);
        exSpec.getExchangeSpecificParameters().put(QuoineExchange.KEY_USER_SECRET, TOKEN_SECRET);

        return (QuoineExchange)ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    private static void generic(AccountService accountService) throws IOException {

        AccountInfo accountInfo = accountService.getAccountInfo();

        System.out.println(accountInfo.toString());
    }

    private static void raw(QuoineAccountServiceRaw quoineAccountServiceRaw) throws IOException {

        final FiatAccount[] quoineFiatAccountInfo = quoineAccountServiceRaw.getQuoineFiatAccountInfo();
        System.out.println(Arrays.toString(quoineFiatAccountInfo));

        System.out.println(Arrays.toString(quoineAccountServiceRaw.getQuoineAccountBalance()));
        System.out.println(Arrays.toString(quoineAccountServiceRaw.getQuoineTradingAccountInfo()));
    }



}
