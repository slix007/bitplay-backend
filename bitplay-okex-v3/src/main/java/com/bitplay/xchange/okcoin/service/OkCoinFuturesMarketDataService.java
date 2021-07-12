package com.bitplay.xchange.okcoin.service;

import java.io.IOException;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.marketdata.Ticker;
import com.bitplay.xchange.dto.marketdata.Trades;
import com.bitplay.xchange.okcoin.FuturesContract;
import com.bitplay.xchange.okcoin.OkCoinAdapters;
import com.bitplay.xchange.okcoin.dto.marketdata.OkcoinForecastPrice;
import com.bitplay.xchange.okcoin.dto.marketdata.OkcoinIndexPrice;
import com.bitplay.xchange.okcoin.dto.marketdata.OkcoinMarkPrice;
import com.bitplay.xchange.service.marketdata.MarketDataService;

public class OkCoinFuturesMarketDataService extends OkCoinMarketDataServiceRaw implements MarketDataService {
  /** Default contract to use */
  private final FuturesContract futuresContract;

  /**
   * Constructor
   *
   * @param exchange
   */
  public OkCoinFuturesMarketDataService(Exchange exchange, FuturesContract futuresContract) {

    super(exchange);

    this.futuresContract = futuresContract;
  }

  @Override
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    if (args.length > 0) {
      return OkCoinAdapters.adaptTicker(getFuturesTicker(currencyPair, (FuturesContract) args[0]), currencyPair);
    } else {
      return OkCoinAdapters.adaptTicker(getFuturesTicker(currencyPair, futuresContract), currencyPair);
    }
  }

  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    if (args.length > 0) {
      return OkCoinAdapters.adaptOrderBook(getFuturesDepth(currencyPair, (FuturesContract) args[0]), currencyPair);
    } else {
      return OkCoinAdapters.adaptOrderBook(getFuturesDepth(currencyPair, futuresContract), currencyPair);
    }
  }

  @Override
  public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
    if (args.length > 0) {
      return OkCoinAdapters.adaptTrades(getFuturesTrades(currencyPair, (FuturesContract) args[0]), currencyPair);
    } else {
      return OkCoinAdapters.adaptTrades(getFuturesTrades(currencyPair, futuresContract), currencyPair);
    }
  }

  public OkcoinForecastPrice getFuturesForecastPrice(CurrencyPair currencyPair) throws IOException {
    return getFuturesEstimatedDeliveryPrice(currencyPair);
  }

  public OkcoinMarkPrice getFuturesMarkPrice(CurrencyPair currencyPair) throws IOException {
    return getFuturesMarkPrice(currencyPair, futuresContract);
  }

  public OkcoinIndexPrice getFuturesIndexPrice(CurrencyPair currencyPair) throws IOException {
    return getFuturesIndexPrice(currencyPair, futuresContract);
  }

}
