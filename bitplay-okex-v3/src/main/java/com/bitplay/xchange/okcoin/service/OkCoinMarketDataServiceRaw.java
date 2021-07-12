package com.bitplay.xchange.okcoin.service;

import java.io.IOException;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.okcoin.FuturesContract;
import com.bitplay.xchange.okcoin.OkCoin;
import com.bitplay.xchange.okcoin.OkCoinAdapters;
import com.bitplay.xchange.okcoin.dto.marketdata.OkCoinDepth;
import com.bitplay.xchange.okcoin.dto.marketdata.OkCoinTickerResponse;
import com.bitplay.xchange.okcoin.dto.marketdata.OkCoinTrade;
import com.bitplay.xchange.okcoin.dto.marketdata.OkcoinForecastPrice;
import com.bitplay.xchange.okcoin.dto.marketdata.OkcoinIndexPrice;
import com.bitplay.xchange.okcoin.dto.marketdata.OkcoinMarkPrice;
import si.mazi.rescu.RestProxyFactory;

public class OkCoinMarketDataServiceRaw extends OkCoinBaseService {

  private final OkCoin okCoin;

  /**
   * Constructor
   *
   * @param exchange
   */
  public OkCoinMarketDataServiceRaw(Exchange exchange) {

    super(exchange);

    okCoin = RestProxyFactory.createProxy(OkCoin.class, exchange.getExchangeSpecification().getSslUri());
  }

  public OkCoinTickerResponse getTicker(CurrencyPair currencyPair) throws IOException {

    return okCoin.getTicker("1", OkCoinAdapters.adaptSymbol(currencyPair));
  }

  public OkCoinTickerResponse getFuturesTicker(CurrencyPair currencyPair, FuturesContract prompt) throws IOException {

    return okCoin.getFuturesTicker(OkCoinAdapters.adaptSymbol(currencyPair), prompt.getName());
  }

  public OkCoinDepth getDepth(CurrencyPair currencyPair) throws IOException {

    return okCoin.getDepth("1", OkCoinAdapters.adaptSymbol(currencyPair));
  }

  public OkCoinDepth getFuturesDepth(CurrencyPair currencyPair, FuturesContract prompt) throws IOException {

    return okCoin.getFuturesDepth("1", OkCoinAdapters.adaptSymbol(currencyPair), prompt.getName().toLowerCase());
  }

  public OkCoinTrade[] getTrades(CurrencyPair currencyPair) throws IOException {

    return okCoin.getTrades("1", OkCoinAdapters.adaptSymbol(currencyPair));
  }

  public OkCoinTrade[] getTrades(CurrencyPair currencyPair, long since) throws IOException {

    return okCoin.getTrades("1", OkCoinAdapters.adaptSymbol(currencyPair), since);
  }

  public OkCoinTrade[] getFuturesTrades(CurrencyPair currencyPair, FuturesContract prompt) throws IOException {

    return okCoin.getFuturesTrades("1", OkCoinAdapters.adaptSymbol(currencyPair), prompt.getName().toLowerCase());
  }

  public OkcoinForecastPrice getFuturesEstimatedDeliveryPrice(CurrencyPair symbol) throws IOException {
    return okCoin.getFuturesEstimatedDeliveryPrice(OkCoinAdapters.adaptSymbol(symbol));
  }

  public OkcoinMarkPrice getFuturesMarkPrice(CurrencyPair symbol, FuturesContract prompt) throws IOException {
    return okCoin.getFuturesMarkPrice(OkCoinAdapters.adaptSymbol(symbol), prompt.getName().toLowerCase());
  }

  public OkcoinIndexPrice getFuturesIndexPrice(CurrencyPair symbol, FuturesContract prompt) throws IOException {
    return okCoin.getFuturesIndexPrice(OkCoinAdapters.adaptSymbol(symbol), prompt.getName().toLowerCase());
  }
}
