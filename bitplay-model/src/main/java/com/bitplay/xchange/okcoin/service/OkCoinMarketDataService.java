package com.bitplay.xchange.okcoin.service;

import java.io.IOException;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.marketdata.Ticker;
import com.bitplay.xchange.dto.marketdata.Trades;
import com.bitplay.xchange.okcoin.OkCoinAdapters;
import com.bitplay.xchange.okcoin.dto.marketdata.OkCoinTrade;
import com.bitplay.xchange.service.marketdata.MarketDataService;

public class OkCoinMarketDataService extends OkCoinMarketDataServiceRaw implements MarketDataService {

  /**
   * Constructor
   *
   * @param exchange
   */
  public OkCoinMarketDataService(Exchange exchange) {

    super(exchange);
  }

  @Override
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    return OkCoinAdapters.adaptTicker(getTicker(currencyPair), currencyPair);
  }

  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    return OkCoinAdapters.adaptOrderBook(getDepth(currencyPair), currencyPair);
  }

  @Override
  public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
    final OkCoinTrade[] trades;

    if (args.length == 0) {
      trades = getTrades(currencyPair);
    } else {
      trades = getTrades(currencyPair, (Long) args[0]);
    }
    return OkCoinAdapters.adaptTrades(trades, currencyPair);
  }
}
