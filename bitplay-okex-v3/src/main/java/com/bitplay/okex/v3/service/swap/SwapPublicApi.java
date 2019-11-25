package com.bitplay.okex.v3.service.swap;

import com.bitplay.model.EstimatedPrice;
import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.service.swap.api.SwapMarketApiServiceImpl;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import java.io.IOException;
import java.math.BigDecimal;

public class SwapPublicApi extends SwapMarketApiServiceImpl {

    public SwapPublicApi(ApiConfiguration config) {
        super(config);
    }

    @Override
    public OrderBook getInstrumentBook(String instrumentId, CurrencyPair currencyPair) {
        return null;
    }

    @Override
    public EstimatedPrice getEstimatedPrice(String instrumentId) {
//        final com.bitplay.okex.v3.dto.futures.result.EstimatedPrice r = getEstimatedPriceApi(instrumentId);
//        final BigDecimal price = r.getSettlement_price() != null ? r.getSettlement_price() : BigDecimal.ZERO;
//        return new EstimatedPrice(price, r.getTimestamp());
        return null;
    }
}
