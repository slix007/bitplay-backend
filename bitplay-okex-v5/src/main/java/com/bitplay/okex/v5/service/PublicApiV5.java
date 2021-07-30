package com.bitplay.okex.v5.service;

import com.bitplay.externalapi.PublicApi;
import com.bitplay.model.EstimatedPrice;
import com.bitplay.model.SwapSettlement;
import com.bitplay.okex.v5.ApiConfigurationV5;
import com.bitplay.okex.v5.client.ApiClient;
import com.bitplay.okex.v5.dto.adapter.BookAdapter;
import com.bitplay.okex.v5.dto.adapter.adapter.SwapConverter;
import com.bitplay.okex.v5.dto.result.Book;
import com.bitplay.okex.v5.dto.result.EstimatedPriceDto;
import com.bitplay.okex.v5.dto.result.SwapFundingTime;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.okcoin.FuturesContract;
import java.math.BigDecimal;
import java.util.Date;

public class PublicApiV5 implements PublicApi {

    private ApiClient client;
    private MarketApiV5 api;
    private String instType;

    public PublicApiV5(ApiConfigurationV5 config, FuturesContract futuresContract) {
        this.client = new ApiClient(config);
        this.api = client.createService(MarketApiV5.class);
        instType = defineInstType(futuresContract);
    }

    private String defineInstType(FuturesContract futuresContract) {
        //SPOT
        //MARGIN
        //SWAP
        //FUTURES
        //OPTION
        return futuresContract.getName().equals("swap") ? "SWAP" : "FUTURES";
    }


    public Book getInstrumentBookApi(String instrumentId) {
        return this.client.executeSync(this.api.getBook(instrumentId, 20));
//        return this.client.executeSync(this.api.getInstrumentBook(instType));
    }

//    public SwapFundingTime getSwapFundingTime(String instrumentId) {
//        return this.client.executeSync(this.api.getSwapFundingTime(instrumentId));
//    }


    @Override
    public OrderBook getInstrumentBook(String instrumentId, CurrencyPair currencyPair) {
        final Book book = getInstrumentBookApi(instrumentId);
        final OrderBook orderBook = BookAdapter.convertBook(book, currencyPair);
//        System.out.println(book);
        return orderBook;
    }

    @Override
    public EstimatedPrice getEstimatedPrice(String instrumentId) {
        final EstimatedPriceDto dto = this.client.executeSync(this.api.getInstrumentEstimatedPrice(instrumentId));
        BigDecimal price = BigDecimal.ZERO;
        Date ts = null;
        if (dto.getData() != null && !dto.getData().isEmpty()) {
            final EstimatedPriceDto.EstimatedPrice r = dto.getData().get(0);
            if (r.getSettlePx() != null) {
                price = r.getSettlePx();
                ts = r.getTs();
            }
        }
        return new EstimatedPrice(price, ts);
    }

    @Override
    public SwapSettlement getSwapSettlement(String instrumentId) {
        final SwapFundingTime swapFundingTime = this.client.executeSync(this.api.getSwapFundingTime(instrumentId));
        return SwapConverter.convertFunding(swapFundingTime.getData());
    }
}
