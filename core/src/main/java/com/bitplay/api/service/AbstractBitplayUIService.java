package com.bitplay.api.service;

import com.bitplay.api.domain.AccountInfoJson;
import com.bitplay.api.domain.LiquidationInfoJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TickerJson;
import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.api.domain.ob.FutureIndexJson;
import com.bitplay.api.domain.ob.OrderBookJson;
import com.bitplay.api.domain.ob.OrderJson;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.FullBalance;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.model.AccountBalance;
import com.bitplay.model.Pos;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.utils.Utils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.ContractLimitOrder;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;

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
    Function<LimitOrder, OrderJson> toOrderJson = o -> {
        String amountInBtc = "";
        if (o instanceof ContractLimitOrder) {
            final BigDecimal inBtc = ((ContractLimitOrder) o).getAmountInBaseCurrency();
            amountInBtc = inBtc != null ? inBtc.toPlainString() : "";
        }

        return new OrderJson(
                "",
                o.getId(),
                o.getStatus() != null ? o.getStatus().toString() : null,
                o.getCurrencyPair().toString(),
                o.getLimitPrice().toPlainString(),
                o.getTradableAmount().toPlainString(),
                o.getType() != null ? o.getType().toString() : "null",
                o.getTimestamp() != null ? LocalDateTime.ofInstant(o.getTimestamp().toInstant(), ZoneId.systemDefault()).toLocalTime().toString() : null,
                amountInBtc
        );
    };

    Function<FplayOrder, OrderJson> openOrderToJson = ord -> {
        Order o = ord.getOrder();
        String amountInBtc = "";
        if (o instanceof ContractLimitOrder) {
            final BigDecimal inBtc = ((ContractLimitOrder) o).getAmountInBaseCurrency();
            amountInBtc = inBtc != null ? inBtc.toPlainString() : "";
        }

        final LimitOrder limO = (LimitOrder) o;
        return new OrderJson(
                ord.getCounterWithPortion(),
                ord.getOrderId(),
                o.getStatus() != null ? o.getStatus().toString() : null,
                o.getCurrencyPair() != null ? o.getCurrencyPair().toString() : "",
                limO.getLimitPrice() != null ? limO.getLimitPrice().toPlainString() : "",
                o.getTradableAmount().toPlainString(),
                o.getType() != null ? o.getType().toString() : "null",
                o.getTimestamp() != null ? LocalDateTime.ofInstant(o.getTimestamp().toInstant(), ZoneId.systemDefault()).toLocalTime().toString() : null,
                amountInBtc
        );
    };

    public abstract T getBusinessService();

    public abstract FutureIndexJson getFutureIndex();

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
        return convertOrderBookAndFilter(
                getBusinessService().getOrderBook(),
                getBusinessService().getTicker()
        );
    }

    protected OrderBookJson convertOrderBookAndFilter(OrderBook orderBook, Ticker ticker) {
        final OrderBookJson orderJson = new OrderBookJson();
        final List<LimitOrder> bestBids = Utils.getBestBids(orderBook, 5);
        orderJson.setBid(bestBids.stream()
                .map(toOrderJson)
                .collect(Collectors.toList()));
        final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook, 5);
        orderJson.setAsk(bestAsks.stream()
                .map(toOrderJson)
                .collect(Collectors.toList()));
        orderJson.setLastPrice(ticker != null ? ticker.getLast() : null);

        try {
            orderJson.setFutureIndex(getFutureIndex());
        } catch (NotYetInitializedException e) {
            orderJson.setFutureIndex(FutureIndexJson.empty());
        }

        return orderJson;
    }

    public AccountInfoJson getFullAccountInfo() {

        final FullBalance fullBalance = getBusinessService().getFullBalance();
        if (fullBalance.getAccountBalance() == null) {
            return AccountInfoJson.error();
        }

        final AccountBalance account = fullBalance.getAccountBalance();
        final Pos position = fullBalance.getPos();

        final BigDecimal available = account.getAvailable();
        final BigDecimal wallet = account.getWallet();
        final BigDecimal margin = account.getMargin();
        final BigDecimal upl = account.getUpl();
        final BigDecimal quAvg = getBusinessService().getArbitrageService().getUsdQuote();
        final BigDecimal liqPrice = position.getLiquidationPrice();
        final BigDecimal eMark = account.getEMark();
        final BigDecimal eLast = account.getELast();
        final BigDecimal eBest = account.getEBest();
        final BigDecimal eAvg = account.getEAvg();

        final String entryPrice = String.format("long/short:%s/%s; %s",
                position.getPriceAvgLong() != null ? position.getPriceAvgLong().toPlainString() : null,
                position.getPriceAvgShort() != null ? position.getPriceAvgShort().toPlainString() : null,
                fullBalance.getTempValues());
        if (available == null || wallet == null || margin == null || upl == null
                || position.getLeverage() == null || quAvg == null) {
//            throw new IllegalStateException("Balance and/or Position are not yet defined. entryPrice=" + entryPrice);
            return new AccountInfoJson("error", "error", "error", "error", "error", "error", "error", "error", "error", "error",
                    "error", "error", "error", "error", "error", "error", "error",
                    entryPrice, "error", "error");
        }

        final String ethBtcBid1 = getBusinessService().getEthBtcTicker() != null ? getBusinessService().getEthBtcTicker().getBid().toPlainString() : null;

        final BigDecimal longAvailToClose = position.getLongAvailToClose() != null ? position.getLongAvailToClose() : BigDecimal.ZERO;
        final BigDecimal shortAvailToClose = position.getShortAvailToClose() != null ? position.getShortAvailToClose() : BigDecimal.ZERO;
        final String plPos = position.getPlPos() != null ? position.getPlPos().toPlainString() : "";
        return new AccountInfoJson(
                wallet.toPlainString(),
                available.toPlainString(),
                margin.toPlainString(),
                getPositionString(position),
                upl.toPlainString(),
                position.getLeverage().toPlainString(),
                getBusinessService().getAffordable().getForLong().toPlainString(),
                getBusinessService().getAffordable().getForShort().toPlainString(),
                longAvailToClose.toPlainString(),
                shortAvailToClose.toPlainString(),
                quAvg.toPlainString(),
                ethBtcBid1,
                liqPrice == null ? null : liqPrice.toPlainString(),
                eMark != null ? eMark.toPlainString() : "0",
                eLast != null ? eLast.toPlainString() : "0",
                eBest != null ? eBest.toPlainString() : "0",
                eAvg != null ? eAvg.toPlainString() : "0",
                entryPrice,
                account.toString(),
                plPos);
    }

    protected String getPositionString(final Pos position) {
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

    public List<OrderJson> getOpenOrders() {
        return getBusinessService().getOpenOrders().stream()
                .map(openOrderToJson)
                .collect(Collectors.toList());

    }

    public ResultJson moveOpenOrder(OrderJson orderJson) {
        final String id = orderJson.getId();
        final MoveResponse response = getBusinessService().moveMakerOrderFromGui(id);
        return new ResultJson(response.getMoveOrderStatus().toString(), response.getDescription());
    }

    public LiquidationInfoJson getLiquidationInfoJson() {
        final LiqInfo liqInfo = getBusinessService().getLiqInfo();
        final LiqParams liqParams = getBusinessService().getPersistenceService().fetchLiqParams(getBusinessService().getName());
        String dqlString = liqInfo.getDqlString();
        if (dqlString != null && dqlString.startsWith("o_DQL = na")) {
            dqlString = "o_DQL = na";
        }
        return new LiquidationInfoJson(dqlString,
                liqInfo.getDmrlString(),
                String.format("DQL: %s ... %s", liqParams.getDqlMin(), liqParams.getDqlMax()),
                String.format("DMRL: %s ... %s", liqParams.getDmrlMin(), liqParams.getDmrlMax())
        );
    }

    public LiquidationInfoJson resetLiquidationInfoJson() {
        getBusinessService().resetLiqInfo();
        return getLiquidationInfoJson();
    }

    protected OrderType convertOrderType(TradeRequestJson.Type type) {
        Order.OrderType orderType;
        switch (type) {
            case BUY:
                orderType = Order.OrderType.BID;
                break;
            case SELL:
                orderType = Order.OrderType.ASK;
                break;
            default:
//                return new TradeResponseJson("Wrong orderType", "Wrong orderType");
                throw new IllegalArgumentException("No such order type " + type);
        }
        return orderType;
    }

    protected SignalType convertSignalType(TradeRequestJson.Type type) {
        SignalType signalType;
        switch (type) {
            case BUY:
                signalType = SignalType.MANUAL_BUY;
                break;
            case SELL:
                signalType = SignalType.MANUAL_SELL;
                break;
            default:
                throw new IllegalArgumentException("No such order type " + type);
        }
        return signalType;
    }

    public TradeResponseJson closeAllPos() {
        final TradeResponse tradeResponse = getBusinessService().closeAllPos();
        return new TradeResponseJson(tradeResponse.getOrderId(), tradeResponse.getErrorCode());
    }
}
