package org.knowm.xchange.bitmex.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexAccountService extends BitmexAccountServiceRaw implements AccountService {

    public BitmexAccountService(Exchange exchange) {
        super(exchange);
    }

    public AccountInfo getAccountInfo() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotYetImplementedForExchangeException();
    }

    public String withdrawFunds(Currency currency, BigDecimal amount, String address) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotYetImplementedForExchangeException();
    }

    public String requestDepositAddress(Currency currency, String... args) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotYetImplementedForExchangeException();
    }

    public Position fetchPositionInfo(String symbol) throws IOException {
        final List<io.swagger.client.model.Position> positions = bitmexAuthenitcatedApi.position(exchange.getExchangeSpecification().getApiKey(),
                signatureCreator,
                exchange.getNonceFactory());

        io.swagger.client.model.Position pos = positions.stream()
                .filter(position -> position.getSymbol() != null && position.getSymbol().equals(symbol))
                .findFirst()
                .orElse(null);

        return BitmexAdapters.adaptBitmexPosition(pos, symbol);
    }
}
