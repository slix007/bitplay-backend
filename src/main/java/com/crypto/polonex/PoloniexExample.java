package com.crypto.polonex;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.poloniex.PoloniexExchange;
import org.knowm.xchange.poloniex.service.PoloniexAccountServiceRaw;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
public class PoloniexExample {

    private static String KEY = "2326PK47-9500PEQV-S64511G1-1HF2V48N";
    private static String SECRET = "2de990fecb2ca516a8cd40fa0ffc8f95f4fc8021e3f7ee681972493c10311c260d26b35c0f2e41adec027056711e2e7b1eaa6cde7d8f679aa871e0a1a801c8fa";

    static PoloniexExchange getExchange() {

        ExchangeSpecification spec = new ExchangeSpecification(PoloniexExchange.class);
        spec.setApiKey(KEY);
        spec.setSecretKey(SECRET);

        return (PoloniexExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    }

    public void doWork() {


        try {
//            CertHelper.trustAllCerts();
            PoloniexExchange poloniex = getExchange();
            final ExchangeMetaData exchangeMetaData = poloniex.getExchangeMetaData();
            final Map<CurrencyPair, CurrencyPairMetaData> currencyPairs = exchangeMetaData.getCurrencyPairs();
            System.out.println("Pair0=" + currencyPairs.get(0));

            System.out.println("Currency0=" + exchangeMetaData.getCurrencies().get(0));
            List<CurrencyPairMetaData> selected = new ArrayList<>();

            Map<CurrencyPair, CurrencyPairMetaData> selectedMeta = new HashMap<>();

            exchangeMetaData.getCurrencyPairs().forEach((pair, currencyPairMetaData) -> {
                if (pair.base == Currency.BTC
                        //&& pair.counter == Currency.USD
                        ) {
                    selected.add(currencyPairMetaData);
                    selectedMeta.put(pair, currencyPairMetaData);
                }
            });

//            selected.forEach(currencyPairMetaData -> {
//                System.out.println("currencyPairMetaData=" + currencyPairMetaData);
//            });

            System.out.println("exchangeMetaData=" + selectedMeta);



            final MarketDataService marketDataService = poloniex.getMarketDataService();
            System.out.println(marketDataService.getTrades(selectedMeta.keySet().iterator().next()));


            AccountService accountService = poloniex.getAccountService();
            generic(accountService);
//            raw((PoloniexAccountServiceRaw) accountService);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void generic(AccountService accountService) throws IOException {
        System.out.println("----------GENERIC----------");
//        System.out.println(accountService.requestDepositAddress(Currency.BTC, new String[0]));
        final AccountInfo accountInfo = accountService.getAccountInfo();
//        System.out.println(accountInfo);
//        System.out.println(accountService.withdrawFunds(Currency.BTC, new BigDecimal("0.03"), "13ArNKUYZ4AmXP4EUzSHMAUsvgGok74jWu"));
    }

    private static void raw(PoloniexAccountServiceRaw accountService) throws IOException {
        System.out.println("------------RAW------------");
//        System.out.println(accountService.getDepositAddress("BTC"));
        System.out.println(accountService.getWallets());
    }
}
