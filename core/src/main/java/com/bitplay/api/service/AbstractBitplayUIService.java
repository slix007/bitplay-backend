package com.bitplay.api.service;

import com.bitplay.api.domain.AccountInfoJson;
import com.bitplay.api.domain.FutureIndexJson;
import com.bitplay.api.domain.OrderBookJson;
import com.bitplay.api.domain.OrderJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TickerJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.utils.Utils;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.ContractLimitOrder;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
public abstract class AbstractBitplayUIService<T extends MarketService> {

    Function<LimitOrder, VisualTrade> toVisual = limitOrder -> new VisualTrade(
            limitOrder.getCurrencyPair().toString(),
            limitOrder.getLimitPrice().toString(),
            limitOrder.getTradableAmount().toString(),
            limitOrder.getType().toString(),
            LocalDateTime.ofInstant(limitOrder.getTimestamp().toInstant(), ZoneId.systemDefault())
                    .toLocalTime().toString());
    Function<LimitOrder, OrderJson> toOrderJson = limitOrder -> {
        final OrderJson orderJson = new OrderJson();
        orderJson.setOrderType(limitOrder.getType() != null ? limitOrder.getType().toString() : "null");
        orderJson.setPrice(limitOrder.getLimitPrice().toPlainString());
        orderJson.setAmount(limitOrder.getTradableAmount().toPlainString());
        orderJson.setCurrency(limitOrder.getCurrencyPair().toString());
        orderJson.setTimestamp(limitOrder.getTimestamp() != null
                ? LocalDateTime.ofInstant(limitOrder.getTimestamp().toInstant(), ZoneId.systemDefault()).toLocalTime().toString()
                : null);
        orderJson.setId(limitOrder.getId());
        orderJson.setStatus(limitOrder.getStatus() != null ? limitOrder.getStatus().toString() : null);
        if (limitOrder instanceof ContractLimitOrder) {
            final BigDecimal inBtc = ((ContractLimitOrder) limitOrder).getAmountInBaseCurrency();
            orderJson.setAmountInBtc(inBtc != null ? inBtc.toPlainString() : "");
        }

        return orderJson;
    };

    public abstract List<VisualTrade> fetchTrades();

    public abstract T getBusinessService();

    VisualTrade toVisualTrade(Trade trade) {
        return new VisualTrade(
                trade.getCurrencyPair().toString(),
                trade.getPrice().toPlainString(),
                trade.getTradableAmount().toPlainString(),
                trade.getType().toString(),
                LocalDateTime.ofInstant(trade.getTimestamp().toInstant(), ZoneId.systemDefault())
                        .toLocalTime().toString()
        );
    }

    public OrderBookJson getOrderBook() {
        return convertOrderBookAndFilter(getBusinessService().getOrderBook());
    }

    public AccountInfoJson getAccountInfo() {
        return convertAccountInfo(getBusinessService().getAccountInfo(),
                getBusinessService().getPosition());
    }

    protected OrderBookJson convertOrderBookAndFilter(OrderBook orderBook) {
        final OrderBookJson orderJson = new OrderBookJson();
        final List<LimitOrder> bestBids = Utils.getBestBids(orderBook.getBids(), 100);
        orderJson.setBid(bestBids.stream()
                .filter(limitOrder -> limitOrder.getTradableAmount().compareTo(BigDecimal.ZERO) != 0)
                .map(toOrderJson)
                .collect(Collectors.toList()));
        final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook.getAsks(), 100);
        orderJson.setAsk(bestAsks.stream()
                .filter(limitOrder -> limitOrder.getTradableAmount().compareTo(BigDecimal.ZERO) != 0)
                .map(toOrderJson)
                .collect(Collectors.toList()));
        return orderJson;
    }

    public AccountInfoJson getContractsAccountInfo() {
        final AccountInfoContracts accountInfoContracts = getBusinessService().getAccountInfoContracts();
        if (accountInfoContracts == null) {
            return new AccountInfoJson("error", "error", "error", "error", "error", "error", "error",
                    "error", "error", "error", "error");
        }

        final BigDecimal available = accountInfoContracts.getAvailable();
        final BigDecimal equity = accountInfoContracts.getEquity();
        final BigDecimal wallet = accountInfoContracts.getWallet();
        final BigDecimal margin = accountInfoContracts.getMargin();
        final BigDecimal upl = accountInfoContracts.getUpl();
        final BigDecimal quAvg = getBusinessService().getArbitrageService().calcQuAvg();

        final Position position = getBusinessService().getPosition();
//        String positionString = String.format("%s; leverage=%s",
//                getPositionString(position),
//                position.getLeverage());
//
//        positionString += String.format("; AvailableForLong:%s, AvailableForShort:%s",
//                getBusinessService().getAffordableContractsForLong(),
//                getBusinessService().getAffordableContractsForShort()
//        );

        return new AccountInfoJson(
                wallet.toPlainString(),
                available.toPlainString(),
                equity.toPlainString(),
                margin.toPlainString(),
                getPositionString(position),
                upl.toPlainString(),
                position.getLeverage().toPlainString(),
                getBusinessService().getAffordableContractsForLong().toPlainString(),
                getBusinessService().getAffordableContractsForShort().toPlainString(),
                quAvg.toPlainString(),
                accountInfoContracts.toString());
    }

    protected String getPositionString(final Position position) {
        return String.format("%s%s",
                "+" + position.getPositionLong().toPlainString(),
                "-" + position.getPositionShort().toPlainString());
    }

    protected TickerJson convertTicker(Ticker ticker) {
        final String value = ticker != null ? ticker.toString() : "";
        return new TickerJson(value);
    }

    protected AccountInfoJson convertAccountInfo(AccountInfo accountInfo, Position position) {
        if (accountInfo == null) {
            return new AccountInfoJson();
        }
        final Wallet wallet = accountInfo.getWallet();
        return new AccountInfoJson(
                wallet.getBalance(Currency.BTC).getAvailable().setScale(8, BigDecimal.ROUND_HALF_UP).toPlainString(),
                wallet.getBalance(Currency.USD).getAvailable().toPlainString(),
                accountInfo.toString());
    }

    public List<VisualTrade> getTradeHistory() {
        final UserTrades userTrades = getBusinessService().fetchMyTradeHistory();
        return userTrades.getUserTrades().stream()
                .map(userTrade -> new VisualTrade(
                        userTrade.getCurrencyPair().toString(),
                        userTrade.getPrice().toPlainString(),
                        userTrade.getTradableAmount().toString(),
                        userTrade.getType().toString(),
                        userTrade.getTimestamp().toString()
                ))
                .collect(Collectors.toList());
    }

    public List<OrderJson> getOpenOrders() {
        return getBusinessService().getOpenOrders().stream()
                .filter(limitOrder -> limitOrder.getTradableAmount().compareTo(BigDecimal.ZERO) != 0)
                .map(toOrderJson)
                .collect(Collectors.toList());

    }

    public ResultJson moveOpenOrder(OrderJson orderJson) {
        final String id = orderJson.getId();
        SignalType signalType;
        if (orderJson.getOrderType().equals("ASK")) {
            signalType = SignalType.MANUAL_SELL;
        } else if (orderJson.getOrderType().equals("BID")) {
            signalType = SignalType.MANUAL_BUY;
        } else {
            return new ResultJson(MoveResponse.MoveOrderStatus.EXCEPTION.toString(), "Wrong orderType");
        }

        final MoveResponse response = getBusinessService().moveMakerOrderFromGui(id, signalType);
        return new ResultJson(response.getMoveOrderStatus().toString(), response.getDescription());
    }

    public FutureIndexJson getFutureIndex() {
        final ContractIndex contractIndex = getBusinessService().getContractIndex();
        final String index = String.format("%s (1c=%sbtc)",
                contractIndex.getIndexPrice().toPlainString(),
                getBusinessService().calcBtcInContract());
        final Date timestamp = contractIndex.getTimestamp();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return new FutureIndexJson(index, sdf.format(timestamp));
    }
}
