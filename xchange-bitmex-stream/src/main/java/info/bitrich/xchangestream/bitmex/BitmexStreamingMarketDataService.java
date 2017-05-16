package info.bitrich.xchangestream.bitmex;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.bitrich.xchangestream.bitmex.dto.BitmexDepth;
import info.bitrich.xchangestream.bitmex.dto.BitmexStreamAdapters;
import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingMarketDataService;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import io.reactivex.Observable;

public class BitmexStreamingMarketDataService implements StreamingMarketDataService {
    private final StreamingServiceBitmex service;

    BitmexStreamingMarketDataService(StreamingServiceBitmex service) {
        this.service = service;
    }

    @Override
    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
        return service.subscribeChannel("orderBook10", "orderBook10:XBTUSD")
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                    BitmexDepth bitmexDepth = mapper.treeToValue(s.get("data").get(0), BitmexDepth.class);

                    return BitmexStreamAdapters.adaptBitmexOrderBook(bitmexDepth, currencyPair);
                });
    }

    @Override
    public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... objects) {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... objects) {
        throw new NotYetImplementedForExchangeException();
    }
}
