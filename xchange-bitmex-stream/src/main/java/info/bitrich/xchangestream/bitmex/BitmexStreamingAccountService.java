package info.bitrich.xchangestream.bitmex;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;

import java.io.IOException;
import java.math.BigDecimal;

public class BitmexStreamingAccountService implements AccountService {

    private final BitmexStreamingServiceBitmex service;

    BitmexStreamingAccountService(BitmexStreamingServiceBitmex service) {
        this.service = service;
    }


    public boolean authorize(String apiKey, String secret) {
        return false;

    }

    private String generateBitmexSignature(String apiSecret, String verb, String url, String nonce,
                                           String postdict) {

        return "";
    }


    @Override
    public AccountInfo getAccountInfo() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return null;
    }

    @Override
    public String withdrawFunds(Currency currency, BigDecimal amount, String address) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return null;
    }

    @Override
    public String requestDepositAddress(Currency currency, String... args) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return null;
    }
}
