package org.knowm.xchange.bitmex.service;

import com.bitplay.model.Pos;
import io.swagger.client.model.Instrument;
import java.io.IOException;
import java.math.BigDecimal;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.bitmex.BitmexExchange;
import org.knowm.xchange.bitmex.dto.ArrayListWithHeaders;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;
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
        final ArrayListWithHeaders<io.swagger.client.model.Position> positions;
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
