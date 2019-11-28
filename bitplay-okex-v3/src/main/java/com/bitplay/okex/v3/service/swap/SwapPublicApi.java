package com.bitplay.okex.v3.service.swap;

import com.bitplay.model.EstimatedPrice;
import com.bitplay.model.SwapSettlement;
import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.dto.futures.result.Book;
import com.bitplay.okex.v3.dto.swap.result.SwapFundingTime;
import com.bitplay.okex.v3.service.futures.adapter.BookAdapter;
import com.bitplay.okex.v3.service.swap.adapter.SwapConverter;
import com.bitplay.okex.v3.service.swap.api.SwapMarketApiServiceImpl;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

public class SwapPublicApi extends SwapMarketApiServiceImpl {

    public SwapPublicApi(ApiConfiguration config) {
        super(config);
    }

    @Override
    public OrderBook getInstrumentBook(String instrumentId, CurrencyPair currencyPair) {
        final Book book = getInstrumentBookApi(instrumentId);
        return BookAdapter.convertBook(book, currencyPair);
    }

    @Override
    public EstimatedPrice getEstimatedPrice(String instrumentId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SwapSettlement getSwapSettlement(String instrumentId) {
        final SwapFundingTime swapFundingTime = getSwapFundingTime(instrumentId);
        return SwapConverter.convertFunding(swapFundingTime);
    }
}
