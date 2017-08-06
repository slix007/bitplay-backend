package info.bitrich.xchangestream.bitmex;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import info.bitrich.xchangestream.bitmex.dto.BitmexDepth;
import info.bitrich.xchangestream.bitmex.dto.BitmexInstrument;
import info.bitrich.xchangestream.bitmex.dto.BitmexStreamAdapters;
import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingMarketDataService;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import java.math.BigDecimal;
import java.util.Date;

import io.reactivex.Observable;
import io.swagger.client.model.Instrument;

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

    public Observable<BitmexInstrument> getContractIndexObservable() {
        return service.subscribeChannel("instrument", "instrument:XBTUSD")
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    mapper.registerModule(new JavaTimeModule());

                    Instrument instrument = mapper.treeToValue(s.get("data").get(0), Instrument.class);

                    final BigDecimal indexPrice = instrument.getMarkPrice() != null
                            ? new BigDecimal(instrument.getMarkPrice()).setScale(2, BigDecimal.ROUND_HALF_UP)
                            : null;

                    final BigDecimal fundingRate = instrument.getFundingRate() != null
                            ? new BigDecimal(instrument.getFundingRate()).multiply(BigDecimal.valueOf(100)).setScale(4, BigDecimal.ROUND_HALF_UP)
                            : null;

                    final Date fundingTimestamp = instrument.getFundingTimestamp() != null
                            ? Date.from(instrument.getFundingTimestamp().toInstant())
                            : null;

                    return new BitmexInstrument(
                            indexPrice,
                            Date.from(instrument.getTimestamp().toInstant()),
                            fundingRate,
                            fundingTimestamp);
                });
    }
}
