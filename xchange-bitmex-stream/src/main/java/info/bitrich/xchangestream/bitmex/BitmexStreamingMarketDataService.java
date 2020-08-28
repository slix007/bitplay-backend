package info.bitrich.xchangestream.bitmex;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import info.bitrich.xchangestream.bitmex.dto.BitmexContractIndex;
import info.bitrich.xchangestream.bitmex.dto.BitmexDepth;
import info.bitrich.xchangestream.bitmex.dto.BitmexOrderBook;
import info.bitrich.xchangestream.bitmex.dto.BitmexQuoteLine;
import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.swagger.client.model.Instrument;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class BitmexStreamingMarketDataService implements StreamingMarketDataService {
    private final StreamingServiceBitmex service;

    BitmexStreamingMarketDataService(StreamingServiceBitmex service) {
        this.service = service;
    }

    /**
     * Wanring: this method will response once in 5 sec after 8 Oct 2017.
     * Use {@link #getOrderBookL2(List)}} instead
     */
    @Override
    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
        throw new IllegalArgumentException("Deprecated. Use {@link #getOrderBookL2(List)} instead.");
    }

    public Observable<BitmexOrderBook> getOrderBookL2_25(List<String> symbols) {
        List<String> orderBookSubjects = symbols.stream()
                .map(s -> "orderBookL2_25:" + s).collect(Collectors.toList());//orderBookL2_25:XBTUSD

        return service.subscribeChannel("orderBookL2_25", orderBookSubjects)
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                    return mapper.treeToValue(s, BitmexOrderBook.class);
                });
    }

    public Observable<BitmexOrderBook> getOrderBookL2(List<String> symbols) {
        List<String> orderBookSubjects = symbols.stream()
                .map(s -> "orderBookL2:" + s).collect(Collectors.toList());//orderBookL2_25:XBTUSD

        return service.subscribeChannel("orderBookL2", orderBookSubjects)
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                    return mapper.treeToValue(s, BitmexOrderBook.class);
                });
    }

    public Observable<BitmexDepth> getOrderBookTop10(List<String> symbols) {
        List<String> orderBookSubjects = symbols.stream()
                .map(s -> "orderBook10:" + s).collect(Collectors.toList());//orderBook:XBTUSD

        return service.subscribeChannel("orderBook10", orderBookSubjects)
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    // table = "orderBook10"
                    // action = partial / update
                    // data
//                    s.get("data").get(0);
                    // symbol
                    // bids
                    // asks
                    // timestamp
                    //noinspection UnnecessaryLocalVariable
                    final BitmexDepth data = mapper.treeToValue(s.get("data").get(0), BitmexDepth.class);
                    return data;
                });
    }

    public Observable<BitmexQuoteLine> getQuote(List<String> symbols) {
        List<String> orderBookSubjects = symbols.stream()
                .map(s -> "quote:" + s).collect(Collectors.toList());//orderBook:XBTUSD

        return service.subscribeChannel("quote", orderBookSubjects)
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    // table = "quote"
                    // action = partial / insert
                    // data
//                    s.get("data").get(0);
                    // symbol
                    // bids
                    // asks
                    // timestamp
                    //noinspection UnnecessaryLocalVariable
//                    ObjectMapper mapper = new ObjectMapper();
//                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                    return mapper.treeToValue(s, BitmexQuoteLine.class);

//                    final BitmexQuoteLine data = mapper.treeToValue(s.get("data").get(0), BitmexQuoteLine.class);
//                    return data;
                });
    }

    public Completable unsubscribeOrderBook(List<String> symbols) {
        List<String> subjects = symbols.stream().map(s -> "orderBookL2_25:" + s).collect(Collectors.toList());
        return service.unsubscribeChannel("orderBookL2_25", subjects);
    }

    @Override
    public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... objects) {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... objects) {
        throw new NotYetImplementedForExchangeException();
    }

    public Observable<BitmexContractIndex> getContractIndexObservable(List<String> symbols) {
        List<String> instruments = symbols.stream()
                .map(s -> "instrument:" + s).collect(Collectors.toList());//instrument:XBTUSD

        return service.subscribeChannel("instrument", instruments)
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    mapper.registerModule(new JavaTimeModule());

                    Instrument instrument = mapper.treeToValue(s.get("data").get(0), Instrument.class);

                    final BigDecimal markPrice = instrument.getMarkPrice() != null
                            ? new BigDecimal(instrument.getMarkPrice()).setScale(2, BigDecimal.ROUND_HALF_UP)
                            : null;

                    final BigDecimal lastPrice = instrument.getLastPrice() != null
                            ? new BigDecimal(instrument.getLastPrice()).setScale(2, BigDecimal.ROUND_HALF_UP)
                            : null;

                    final BigDecimal indexPrice = instrument.getIndicativeSettlePrice() != null
                            ? new BigDecimal(instrument.getIndicativeSettlePrice()).setScale(2, BigDecimal.ROUND_HALF_UP)
                            : null;

                    final BigDecimal fundingRate = instrument.getFundingRate() != null
                            ? new BigDecimal(instrument.getFundingRate()).multiply(BigDecimal.valueOf(100)).setScale(4, BigDecimal.ROUND_HALF_UP)
                            : null;

                    final OffsetDateTime fundingTimestamp = instrument.getFundingTimestamp();

                    return new BitmexContractIndex(
                            instrument.getSymbol(),
                            indexPrice,
                            markPrice,
                            lastPrice,
                            instrument.getTimestamp() != null ? Date.from(instrument.getTimestamp().toInstant()) : new Date(),
                            fundingRate,
                            fundingTimestamp);
                });
    }
}
