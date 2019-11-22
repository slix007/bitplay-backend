package com.bitplay.okex.v3.service.swap;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.service.swap.api.SwapMarketApiServiceImpl;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import java.io.IOException;

public class SwapPublicApi extends SwapMarketApiServiceImpl {

    public SwapPublicApi(ApiConfiguration config) {
        super(config);
    }

    @Override
    public OrderBook getInstrumentBook(String instrumentId, CurrencyPair currencyPair) {
        return null;
    }
}
