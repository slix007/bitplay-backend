package com.bitplay.okexv5;

import com.bitplay.core.StreamingMarketDataService;
import com.bitplay.core.helper.WsObjectMapperHelper;
import com.bitplay.okexv5.dto.InstrumentDto;
import com.bitplay.okexv5.dto.marketdata.OkCoinDepth;
import com.bitplay.okexv5.dto.marketdata.OkcoinIndexTicker;
import com.bitplay.okexv5.dto.marketdata.OkcoinMarkPrice;
import com.bitplay.okexv5.dto.marketdata.OkcoinPriceRange;
import com.bitplay.okexv5.dto.marketdata.OkcoinTicker;
import com.bitplay.okexv5.dto.request.RequestDto;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.marketdata.Ticker;
import com.bitplay.xchange.dto.marketdata.Trade;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Observable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by Sergei Shurmin on 02.03.19.
 */
public class OkExStreamingMarketDataService implements StreamingMarketDataService {

    private final OkexStreamingServiceWsToRxV5 service;

    private final ObjectMapper objectMapper = WsObjectMapperHelper.getObjectMapper();

    OkExStreamingMarketDataService(OkexStreamingServiceWsToRxV5 service) {
        this.service = service;
    }

    /**
     * @param currencyPair Currency pair of the order book
     * @param args arg[0]=(String)expDate, arg[1]=(Boolean)depth5(optional)
     * @return observable
     */
    @Override
    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
//        throw new NotSupportedException("Use get getOrderBooks() instead");
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing required params:\n"
                    + "- InstrumentDto\n"
                    + "- isDepth5(optional)");
        }
        final InstrumentDto instrumentDto = (InstrumentDto) args[0];
        final String instrumentId = instrumentDto.getInstrumentId();
        final String channelName = args.length == 2 && (Boolean) args[1]
                ? "futures/depth5:" + instrumentId
                : "futures/depth:" + instrumentId;
        return this.service.subscribeChannel(channelName)
                .filter(s -> s.get("table") != null)
                .filter(s -> s.get("table").asText().contains("depth"))
                .filter(s -> s.get("data") != null)
                .map((s) -> objectMapper.treeToValue(s.get("data").get(0), OkCoinDepth.class))
                .map(r -> OkExAdapters.adaptOrderBook(r, currencyPair))
                ;
    }

    /**
     * @param currencyPair Currency pair of the order book
     * @param args arg[0]=(String)expDate, arg[1]=(Boolean)depth5(optional)
     * @return observable
     */
    @Override
    public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Missing required params:\n"
                    + "- InstrumentDto");
        }
        final String instrument = (String) args[0];
        final String channelName = RequestDto.TICKERS + "/" + instrument;
        return service.subscribeChannel(channelName)
                .map(s -> s.get("data"))
                .filter(Objects::nonNull)
                .flatMap(Observable::fromIterable)
                .map(dataNode -> objectMapper.treeToValue(dataNode, OkcoinTicker.class))
                .map(okCoinTicker -> OkExAdapters.adaptTicker(okCoinTicker, currencyPair))
                .share();
    }

    public Observable<OkcoinPriceRange> getPriceRange(InstrumentDto instrumentDto) {
        final String instrumentId = instrumentDto.getInstrumentId();
        final String channelName = RequestDto.PRICE_LIMIT + "/" + instrumentId;
        return service.subscribeChannel(channelName)
                .map(s -> s.get("data"))
                .filter(Objects::nonNull)
                .flatMap(Observable::fromIterable)
                .map(dataNode -> objectMapper.treeToValue(dataNode, OkcoinPriceRange.class))
                .share();
    }


    public Observable<OkCoinDepth> getOrderBooks(List<InstrumentDto> instruments) {
        List<String> channelNames = new ArrayList<>();
        for (InstrumentDto instrument : instruments) {
            channelNames.add(RequestDto.BOOKS5 + "/" + instrument.getInstrumentId());
        }
        return this.service.subscribeBatchChannels(channelNames)
                .filter(s -> s.get("event") == null
                        && s.get("arg") != null
                        && s.get("arg").get("channel") != null
                        && s.get("arg").get("instId") != null
                        && s.get("data") != null
                )
                .map(s -> s.get("data"))
                .filter(Objects::nonNull)
                .flatMap(Observable::fromIterable)
                .map(dataNode -> objectMapper.treeToValue(dataNode, OkCoinDepth.class))
//                .map(d -> {
//                    final CurrencyPair currencyPair = instrumentIdToCurrencyPair.get(d.getInstrumentId());
//                    return OkExAdapters.adaptOrderBook(d, currencyPair);
//                })
                .share();
    }

    public Observable<OkcoinMarkPrice> getMarkPrices(List<InstrumentDto> instruments) {
        List<String> channelNames = new ArrayList<>();
        for (InstrumentDto instrument : instruments) {
            channelNames.add(RequestDto.MARK_PRICE + "/" + instrument.getInstrumentId());
        }

        return service.subscribeBatchChannels(channelNames)
                .map(s -> s.get("data"))
                .filter(Objects::nonNull)
                .flatMap(Observable::fromIterable)
                .map(dataNode -> objectMapper.treeToValue(dataNode, OkcoinMarkPrice.class))
                .share();
    }

    @SuppressWarnings("Duplicates")
//    public Observable<Ticker> getIndexTickers(List<InstrumentDto> instruments) {
////        List<String> channelNames = new ArrayList<>();
////        Map<String, CurrencyPair> instrumentIdToCurrencyPair = new HashMap<>();
////        for (CurrencyPair currencyPair : currencyPairs) {
////            final String instrumentId = currencyPair.base.getCurrencyCode()
////                    + "-" + currencyPair.counter.getCurrencyCode(); // BTC-USD
////            instrumentIdToCurrencyPair.put(instrumentId, currencyPair);
////            final String channelName = "index/ticker:" + instrumentId;
////            channelNames.add(channelName);
////        }
//        List<String> channelNames = new ArrayList<>();
//        for (InstrumentDto instrument : instruments) {
//            channelNames.add(RequestDto.INDEX_TICKERS + "/" + instrument.getInstrumentId());
//        }
//
//        return service.subscribeBatchChannels(channelNames)
//                .map(s -> s.get("data"))
//                .filter(Objects::nonNull)
//                .flatMap(Observable::fromIterable)
//                .map(dataNode -> objectMapper.treeToValue(dataNode, OkcoinTicker.class))
//                .map(OkExAdapters::adaptTicker)
//                .share();
//    }

    public Observable<Ticker> getIndexTickers(List<CurrencyPair> currencyPairs) {
        List<String> channelNames = new ArrayList<>();
        Map<String, CurrencyPair> instrumentIdToCurrencyPair = new HashMap<>();
        for (CurrencyPair currencyPair : currencyPairs) {
            final String instrumentId = currencyPair.base.getCurrencyCode()
                    + "-" + currencyPair.counter.getCurrencyCode(); // BTC-USD
            instrumentIdToCurrencyPair.put(instrumentId, currencyPair);
            final String channelName = RequestDto.INDEX_TICKERS + "/" + instrumentId;
            channelNames.add(channelName);
        }

        return service.subscribeBatchChannels(channelNames)
                .map(s -> s.get("data"))
                .filter(Objects::nonNull)
                .flatMap(Observable::fromIterable)
                .map(dataNode -> objectMapper.treeToValue(dataNode, OkcoinIndexTicker.class))
                .map(okCoinTicker -> {
                    final CurrencyPair currencyPair = instrumentIdToCurrencyPair.get(okCoinTicker.getInstrumentId());
                    return OkExAdapters.adaptIndexTicker(okCoinTicker, currencyPair);
                })
                .share();
    }

    @Override
    public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
        return null;
    }

//    public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
//        String channel = String.format("ok_sub_spot_%s_%s_deals", currencyPair.base.toString().toLowerCase(), currencyPair.counter.toString().toLowerCase());
//
//        if (args.length > 0) {
//            FuturesContract contract = (FuturesContract) args[0];
//            channel = String.format("ok_sub_future%s_%s_trade_%s", currencyPair.counter.toString().toLowerCase(), currencyPair.base.toString().toLowerCase(), contract.getName());
//        }
//
//        return service.subscribeChannel(channel)
//                .map(s -> {
//                    String[][] trades = objectMapper.treeToValue(s.get("data"), String[][].class);
//
//                    // I don't know how to parse this array of arrays in Jacson.
//                    OkCoinWebSocketTrade[] okCoinTrades = new OkCoinWebSocketTrade[trades.length];
//                    for (int i = 0; i < trades.length; ++i) {
//                        OkCoinWebSocketTrade okCoinWebSocketTrade = new OkCoinWebSocketTrade(trades[i]);
//                        okCoinTrades[i] = okCoinWebSocketTrade;
//                    }
//
//                    return OkCoinAdapters.adaptTrades(okCoinTrades, currencyPair);
//                }).flatMapIterable(Trades::getTrades);
//    }

//    private void printObj(Object obj) {
//        try {
//            final String s = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
//            System.out.println(s);
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
//    }
}
