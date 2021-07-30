package com.bitplay.xchange.okcoin;

import static com.bitplay.xchange.currency.Currency.BTC;
import static com.bitplay.xchange.currency.Currency.ETH;
import static com.bitplay.xchange.currency.Currency.LTC;
import static com.bitplay.xchange.currency.Currency.USD;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.bitplay.xchange.currency.Currency;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderStatus;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.account.AccountInfo;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.dto.account.Balance;
import com.bitplay.xchange.dto.account.Wallet;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.marketdata.Ticker;
import com.bitplay.xchange.dto.marketdata.Trade;
import com.bitplay.xchange.dto.marketdata.Trades;
import com.bitplay.xchange.dto.marketdata.Trades.TradeSortType;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.dto.trade.OpenOrders;
import com.bitplay.xchange.dto.trade.UserTrade;
import com.bitplay.xchange.dto.trade.UserTrades;
import com.bitplay.xchange.okcoin.dto.account.OkCoinFunds;
import com.bitplay.xchange.okcoin.dto.account.OkCoinFuturesInfoCross;
import com.bitplay.xchange.okcoin.dto.account.OkCoinFuturesUserInfoCross;
import com.bitplay.xchange.okcoin.dto.account.OkCoinUserInfo;
import com.bitplay.xchange.okcoin.dto.account.OkcoinFuturesFundsCross;
import com.bitplay.xchange.okcoin.dto.marketdata.OkCoinDepth;
import com.bitplay.xchange.okcoin.dto.marketdata.OkCoinTickerResponse;
import com.bitplay.xchange.okcoin.dto.marketdata.OkCoinTrade;
import com.bitplay.xchange.okcoin.dto.trade.OkCoinFuturesOrder;
import com.bitplay.xchange.okcoin.dto.trade.OkCoinFuturesOrderResult;
import com.bitplay.xchange.okcoin.dto.trade.OkCoinFuturesTradeHistoryResult;
import com.bitplay.xchange.okcoin.dto.trade.OkCoinFuturesTradeHistoryResult.TransactionType;
import com.bitplay.xchange.okcoin.dto.trade.OkCoinOrder;
import com.bitplay.xchange.okcoin.dto.trade.OkCoinOrderResult;

public final class OkCoinAdapters {

  private static final Balance zeroUsdBalance = new Balance(USD, BigDecimal.ZERO);

  private OkCoinAdapters() {

  }

  private static BigDecimal getOrZero(String key, Map<String, BigDecimal> map) {

    if (map != null && map.containsKey(key)) {
      return map.get(key);
    } else {
      return BigDecimal.ZERO;
    }
  }

  public static String adaptSymbol(CurrencyPair currencyPair) {

    return currencyPair.base.getCurrencyCode().toLowerCase() + "_" + currencyPair.counter.getCurrencyCode().toLowerCase();
  }

  public static CurrencyPair adaptSymbol(String symbol) {

    String[] currencies = symbol.toUpperCase().split("_");
    return new CurrencyPair(currencies[0], currencies[1]);
  }

  public static Ticker adaptTicker(OkCoinTickerResponse tickerResponse, CurrencyPair currencyPair) {
    long date = tickerResponse.getDate();
    return new Ticker.Builder().currencyPair(currencyPair).high(tickerResponse.getTicker().getHigh()).low(tickerResponse.getTicker().getLow())
        .bid(tickerResponse.getTicker().getBuy()).ask(tickerResponse.getTicker().getSell()).last(tickerResponse.getTicker().getLast())
        .volume(tickerResponse.getTicker().getVol()).timestamp(new Date(date * 1000L)).build();
  }

  public static OrderBook adaptOrderBook(OkCoinDepth depth, CurrencyPair currencyPair) {

    List<LimitOrder> asks = adaptLimitOrders(OrderType.ASK, depth.getAsks(), currencyPair, depth.getTimestamp());
    Collections.reverse(asks);

    List<LimitOrder> bids = adaptLimitOrders(OrderType.BID, depth.getBids(), currencyPair, depth.getTimestamp());
    return new OrderBook(depth.getTimestamp(), asks, bids);
  }

  public static Trades adaptTrades(OkCoinTrade[] trades, CurrencyPair currencyPair) {

    List<Trade> tradeList = new ArrayList<Trade>(trades.length);
    for (int i = 0; i < trades.length; i++) {
      OkCoinTrade trade = trades[i];
      tradeList.add(adaptTrade(trade, currencyPair));
    }
    long lastTid = trades.length > 0 ? (trades[trades.length - 1].getTid()) : 0L;
    return new Trades(tradeList, lastTid, TradeSortType.SortByTimestamp);
  }

  public static AccountInfo adaptAccountInfo(OkCoinUserInfo userInfo) {

    OkCoinFunds funds = userInfo.getInfo().getFunds();

    Map<String, Balance.Builder> builders = new TreeMap<String, Balance.Builder>();

    for (Map.Entry<String, BigDecimal> available : funds.getFree().entrySet()) {
      builders.put(available.getKey(), new Balance.Builder().currency(Currency.getInstance(available.getKey())).available(available.getValue()));
    }

    for (Map.Entry<String, BigDecimal> frozen : funds.getFreezed().entrySet()) {
      Balance.Builder builder = builders.get(frozen.getKey());
      if (builder == null) {
        builder = new Balance.Builder().currency(Currency.getInstance(frozen.getKey()));
      }
      builders.put(frozen.getKey(), builder.frozen(frozen.getValue()));
    }

    for (Map.Entry<String, BigDecimal> borrowed : funds.getBorrow().entrySet()) {
      Balance.Builder builder = builders.get(borrowed.getKey());
      if (builder == null) {
        builder = new Balance.Builder().currency(Currency.getInstance(borrowed.getKey()));
      }
      builders.put(borrowed.getKey(), builder.borrowed(borrowed.getValue()));
    }

    List<Balance> wallet = new ArrayList(builders.size());

    for (Balance.Builder builder : builders.values()) {
      wallet.add(builder.build());
    }

    return new AccountInfo(new Wallet(wallet));
  }

  @SuppressWarnings("Duplicates")
  private static Balance adaptBalance(Currency currency, OkcoinFuturesFundsCross fundsCross) {
    final BigDecimal equity = fundsCross.getAccountRights().setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal margin = fundsCross.getKeepDeposits().setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal upl = fundsCross.getProfitUnreal().setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal wallet = equity.subtract(upl).setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal available = equity.subtract(margin).setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal rpl = fundsCross.getProfitReal().setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal riskRate = fundsCross.getRiskRate().setScale(8, BigDecimal.ROUND_HALF_UP);
    return new Balance(currency,
            fundsCross.getAccountRights(),
            available,
            margin);
  }

  @SuppressWarnings("Duplicates")
  public static AccountInfoContracts adaptAccountInfoContractsFutures(Currency currency, OkCoinFuturesUserInfoCross futureUserInfo) {
    OkCoinFuturesInfoCross info = futureUserInfo.getInfo();
    OkcoinFuturesFundsCross fundsCross;
    if (currency == BTC) {
      fundsCross = info.getBtcFunds();
    } else if (currency == ETH) {
      fundsCross = info.getEthFunds();
    } else {
      throw new IllegalArgumentException("Unsupported currency " + currency);
    }
    final BigDecimal equity = fundsCross.getAccountRights().setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal margin = fundsCross.getKeepDeposits().setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal upl = fundsCross.getProfitUnreal().setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal wallet = equity.subtract(upl).setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal available = equity.subtract(margin).setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal rpl = fundsCross.getProfitReal().setScale(8, BigDecimal.ROUND_HALF_UP);
    final BigDecimal riskRate = fundsCross.getRiskRate().setScale(8, BigDecimal.ROUND_HALF_UP);

    return new AccountInfoContracts(wallet, available, null, equity, null, null, margin, upl, rpl, riskRate);

  }

  public static AccountInfo adaptAccountInfoFutures(OkCoinFuturesUserInfoCross futureUserInfo) {
    OkCoinFuturesInfoCross info = futureUserInfo.getInfo();
    OkcoinFuturesFundsCross btcFunds = info.getBtcFunds();
    OkcoinFuturesFundsCross ltcFunds = info.getLtcFunds();
    OkcoinFuturesFundsCross ethFunds = info.getEthFunds();

    Balance btcBalance = adaptBalance(BTC, btcFunds);
    Balance ltcBalance = adaptBalance(LTC, ltcFunds);
    Balance ethBalance = adaptBalance(ETH, ethFunds);

    return new AccountInfo(new Wallet(zeroUsdBalance, btcBalance, ltcBalance, ethBalance));
  }

  public static OpenOrders adaptOpenOrders(List<OkCoinOrderResult> orderResults) {
    List<LimitOrder> openOrders = new ArrayList<LimitOrder>();

    for (int i = 0; i < orderResults.size(); i++) {
      OkCoinOrderResult orderResult = orderResults.get(i);
      OkCoinOrder[] orders = orderResult.getOrders();
      for (int j = 0; j < orders.length; j++) {
        OkCoinOrder singleOrder = orders[j];
        openOrders.add(adaptOpenOrder(singleOrder));
      }
    }
    return new OpenOrders(openOrders);
  }

  public static OpenOrders adaptOpenOrdersFutures(List<OkCoinFuturesOrderResult> orderResults) {
    List<LimitOrder> openOrders = new ArrayList<LimitOrder>();

    for (int i = 0; i < orderResults.size(); i++) {
      OkCoinFuturesOrderResult orderResult = orderResults.get(i);
      OkCoinFuturesOrder[] orders = orderResult.getOrders();
      for (int j = 0; j < orders.length; j++) {
        OkCoinFuturesOrder singleOrder = orders[j];
        openOrders.add(adaptOpenOrderFutures(singleOrder));
      }
    }
    return new OpenOrders(openOrders);
  }

  public static UserTrades adaptTrades(OkCoinOrderResult orderResult) {

    List<UserTrade> trades = new ArrayList<UserTrade>(orderResult.getOrders().length);
    for (int i = 0; i < orderResult.getOrders().length; i++) {
      OkCoinOrder order = orderResult.getOrders()[i];

      // skip cancels that have not yet been filtered out
      if (order.getDealAmount().equals(BigDecimal.ZERO)) {
        continue;
      }
      trades.add(adaptTrade(order));
    }
    return new UserTrades(trades, TradeSortType.SortByTimestamp);
  }

  public static UserTrades adaptTradesFutures(OkCoinFuturesOrderResult orderResult) {

    List<UserTrade> trades = new ArrayList<UserTrade>(orderResult.getOrders().length);
    for (int i = 0; i < orderResult.getOrders().length; i++) {
      OkCoinFuturesOrder order = orderResult.getOrders()[i];

      // skip cancels that have not yet been filtered out
      if (order.getDealAmount().equals(BigDecimal.ZERO)) {
        continue;
      }
      trades.add(adaptTradeFutures(order));
    }
    return new UserTrades(trades, TradeSortType.SortByTimestamp);
  }

  private static List<LimitOrder> adaptLimitOrders(OrderType type, BigDecimal[][] list, CurrencyPair currencyPair, Date timestamp) {

    List<LimitOrder> limitOrders = new ArrayList<LimitOrder>(list.length);
    for (int i = 0; i < list.length; i++) {
      BigDecimal[] data = list[i];
      limitOrders.add(adaptLimitOrder(type, data, currencyPair, null, timestamp != null ? timestamp : new Date()));
    }
    return limitOrders;
  }

  private static LimitOrder adaptLimitOrder(OrderType type, BigDecimal[] data, CurrencyPair currencyPair, String id, Date timestamp) {

    return new LimitOrder(type, data[1], currencyPair, id, timestamp, data[0]);
  }

  private static Trade adaptTrade(OkCoinTrade trade, CurrencyPair currencyPair) {

    return new Trade(trade.getType().equals("buy") ? OrderType.BID : OrderType.ASK, trade.getAmount(), currencyPair, trade.getPrice(),
        trade.getDate(), "" + trade.getTid());
  }

  private static LimitOrder adaptOpenOrder(OkCoinOrder order) {

    return new LimitOrder(adaptOrderType(order.getType()), order.getAmount(), adaptSymbol(order.getSymbol()), String.valueOf(order.getOrderId()),
        order.getCreateDate(), order.getPrice());
  }

  public static LimitOrder adaptOpenOrderFutures(OkCoinFuturesOrder order) {
    return new LimitOrder(adaptOrderType(order.getType()), order.getAmount(), adaptSymbol(order.getSymbol()), String.valueOf(order.getOrderId()),
        order.getCreatedDate(), order.getPrice(), order.getAvgPrice(), order.getDealAmount(), adaptOrderStatus(order.getStatus()));
  }

  public static OrderType adaptOrderType(String type) {

    switch (type) {
      // type: order type 1: open long, 2: open short, 3: close long, 4: close short
    case "buy":
      return OrderType.BID;
    case "buy_market":
      return OrderType.BID;
    case "sell":
      return OrderType.ASK;
    case "sell_market":
      return OrderType.ASK;
    case "1":
      return OrderType.BID;
    case "2":
      return OrderType.ASK;
    case "3":
      return OrderType.EXIT_BID;
    case "4":
      return OrderType.EXIT_ASK;
    default:
      return null;
    }

  }

  public static OrderStatus adaptOrderStatus(int status) {
    switch (status) {

    case -1:
      return OrderStatus.CANCELED;
    case 0:
      return OrderStatus.NEW;
    case 1:
      return OrderStatus.PARTIALLY_FILLED;
    case 2:
      return OrderStatus.FILLED;
    case 4:
      return OrderStatus.PENDING_CANCEL;
    default:
      return null;
    }

  }

  private static UserTrade adaptTrade(OkCoinOrder order) {

    return new UserTrade(adaptOrderType(order.getType()), order.getDealAmount(), adaptSymbol(order.getSymbol()), order.getPrice(),
        order.getCreateDate(), null, String.valueOf(order.getOrderId()), null, (Currency) null);
  }

  private static UserTrade adaptTradeFutures(OkCoinFuturesOrder order) {

    return new UserTrade(adaptOrderType(order.getType()), order.getDealAmount(), adaptSymbol(order.getSymbol()), order.getPrice(),
        order.getCreatedDate(), null, String.valueOf(order.getOrderId()), null, (Currency) null);
  }

  public static UserTrades adaptTradeHistory(OkCoinFuturesTradeHistoryResult[] okCoinFuturesTradeHistoryResult) {

    List<UserTrade> trades = new ArrayList<UserTrade>();
    long lastTradeId = 0;
    for (OkCoinFuturesTradeHistoryResult okCoinFuturesTrade : okCoinFuturesTradeHistoryResult) {
      //  if (okCoinFuturesTrade.getType().equals(OkCoinFuturesTradeHistoryResult.TransactionType.)) { // skip account deposits and withdrawals.
      OrderType orderType = okCoinFuturesTrade.getType().equals(TransactionType.sell) ? OrderType.ASK : OrderType.BID;
      BigDecimal tradableAmount = BigDecimal.valueOf(okCoinFuturesTrade.getAmount());
      BigDecimal price = okCoinFuturesTrade.getPrice();
      Date timestamp = new Date(okCoinFuturesTrade.getTimestamp());
      long transactionId = okCoinFuturesTrade.getId();
      if (transactionId > lastTradeId) {
        lastTradeId = transactionId;
      }
      final String tradeId = String.valueOf(transactionId);
      final String orderId = String.valueOf(okCoinFuturesTrade.getId());
      final CurrencyPair currencyPair = CurrencyPair.BTC_USD;

      BigDecimal feeAmont = BigDecimal.ZERO;
      UserTrade trade = new UserTrade(orderType, tradableAmount, currencyPair, price, timestamp, tradeId, orderId, feeAmont,
          Currency.getInstance(currencyPair.counter.getCurrencyCode()));
      trades.add(trade);

    }

    return new UserTrades(trades, lastTradeId, TradeSortType.SortByID);
  }
}