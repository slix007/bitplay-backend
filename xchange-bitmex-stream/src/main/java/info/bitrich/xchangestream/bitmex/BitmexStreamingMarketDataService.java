package info.bitrich.xchangestream.bitmex;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import info.bitrich.xchangestream.bitmex.dto.BitmexContractIndex;
import info.bitrich.xchangestream.bitmex.dto.BitmexOrderBook;
import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.Observable;
import io.swagger.client.model.Instrument;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

public class BitmexStreamingMarketDataService implements StreamingMarketDataService {
    private final StreamingServiceBitmex service;

    BitmexStreamingMarketDataService(StreamingServiceBitmex service) {
        this.service = service;
    }

    /**
     * Wanring: this method will response once in 5 sec after 8 Oct 2017.
     * Use {@link #getOrderBookL2(String)} instead
     */
    @Override
    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
        throw new IllegalArgumentException("Deprecated. Use {@link #getOrderBookL2(String)} instead.");
    }

    public Observable<BitmexOrderBook> getOrderBookL2(String symbol) {
        String orderBookL2Channel = "orderBookL2:" + symbol;
        return service.subscribeChannel(orderBookL2Channel, orderBookL2Channel)
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                    @SuppressWarnings("unused")
                    BitmexOrderBook bitmexOrderBook = mapper.treeToValue(s, BitmexOrderBook.class);

                    return bitmexOrderBook;
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
                            Date.from(instrument.getTimestamp().toInstant()),
                            fundingRate,
                            fundingTimestamp);
                });
    }
}
