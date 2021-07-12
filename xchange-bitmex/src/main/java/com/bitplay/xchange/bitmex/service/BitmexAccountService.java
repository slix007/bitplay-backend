package com.bitplay.xchange.bitmex.service;

import com.bitplay.model.Pos;
import com.bitplay.xchange.bitmex.BitmexAdapters;
import com.bitplay.xchange.bitmex.BitmexExchange;
import com.bitplay.xchange.bitmex.dto.ArrayListWithHeaders;
import com.bitplay.xchange.dto.account.AccountInfo;
import com.bitplay.xchange.exceptions.ExchangeException;
import com.bitplay.xchange.exceptions.NotAvailableFromExchangeException;
import com.bitplay.xchange.exceptions.NotYetImplementedForExchangeException;
import com.bitplay.xchange.service.account.AccountService;
import io.swagger.client.model.Instrument;
import io.swagger.client.model.Position;
import java.io.IOException;
import java.math.BigDecimal;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.currency.Currency;
import si.mazi.rescu.HttpStatusIOException;

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

    public String withdrawFunds(Currency currency, BigDecimal amount, String address)
            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotYetImplementedForExchangeException();
    }

    public String requestDepositAddress(Currency currency, String... args)
            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotYetImplementedForExchangeException();
    }

    public Pos fetchPositionInfo(String symbol) throws IOException {
        final ArrayListWithHeaders<Position> positions;
        try {
            positions = bitmexAuthenitcatedApi.position(exchange.getExchangeSpecification().getApiKey(),
                    signatureCreator,
                    exchange.getNonceFactory());
        } catch (HttpStatusIOException e) {
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(e);
            throw e;
        }
        final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
        bitmexStateService.setXrateLimit(positions);

        io.swagger.client.model.Position pos = positions.stream()
                .filter(position -> position.getSymbol() != null && position.getSymbol().equals(symbol))
                .findFirst()
                .orElse(null);

        return BitmexAdapters.adaptBitmexPosition(pos, symbol);
    }

    public Instrument getInstrument(String symbol) throws IOException {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }

        final ArrayListWithHeaders<Instrument> instruments;
        try {
            instruments = bitmexAuthenitcatedApi.instrument(
                    exchange.getExchangeSpecification().getApiKey(),
                    signatureCreator,
                    exchange.getNonceFactory(),
                    symbol,
                    ""
            );
        } catch (HttpStatusIOException e) {
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(e);
            throw e;
        }
        final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
        bitmexStateService.setXrateLimit(instruments);

        Instrument inst = null;
        for (Instrument i : instruments) {
            if (i.getSymbol().equals(symbol)) {
                inst = i;
                break;
            }
        }
        return inst;
    }
}
