package com.bitplay.api.service;

import com.bitplay.api.domain.AccountInfoJson;
import com.bitplay.api.domain.LiquidationInfoJson;
import com.bitplay.api.domain.OrderBookJson;
import com.bitplay.api.domain.OrderJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TickerJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.FullBalance;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.utils.Utils;
import io.reactivex.Observable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.AccountInfoContracts;
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

        return new OrderJson(
                ord.getCounterName(),
                ord.getOrderId(),
                o.getStatus() != null ? o.getStatus().toString() : null,
                o.getCurrencyPair().toString(),
                ((LimitOrder) o).getLimitPrice().toPlainString(),
                o.getTradableAmount().toPlainString(),
                o.getType() != null ? o.getType().toString() : "null",
                o.getTimestamp() != null ? LocalDateTime.ofInstant(o.getTimestamp().toInstant(), ZoneId.systemDefault()).toLocalTime().toString() : null,
                amountInBtc
        );
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
        final List<LimitOrder> bestBids = Utils.getBestBids(orderBook, 5);
        orderJson.setBid(bestBids.stream()
                .map(toOrderJson)
                .collect(Collectors.toList()));
        final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook, 5);
        orderJson.setAsk(bestAsks.stream()
                .map(toOrderJson)
                .collect(Collectors.toList()));
        return orderJson;
    }

    public Observable<AccountInfoJson> getContractsAccountInfoAsync() {
        Observable<AccountInfoJson> o = Observable.fromCallable(() -> this.getContractsAccountInfo());
        return o;
    }

    public AccountInfoJson getContractsAccountInfo() {
        final AccountInfoContracts accountInfoContracts = getBusinessService().getAccountInfoContracts();
        if (accountInfoContracts == null) {
            return new AccountInfoJson("error", "error", "error", "error", "error", "error", "error",
                    "error", "error", "error", "error", "error", "error", "error", "error", "error");
        }

        final BigDecimal available = accountInfoContracts.getAvailable();
        final BigDecimal wallet = accountInfoContracts.getWallet();
        final BigDecimal margin = accountInfoContracts.getMargin();
        final BigDecimal upl = accountInfoContracts.getUpl();
        final BigDecimal quAvg = getBusinessService().getArbitrageService().calcQuAvg();
        final BigDecimal liqPrice = getBusinessService().getPosition().getLiquidationPrice();
        final BigDecimal eMark = accountInfoContracts.geteMark();
        final BigDecimal eLast = accountInfoContracts.geteLast();
        final BigDecimal eBest = accountInfoContracts.geteBest();
        final BigDecimal eAvg = accountInfoContracts.geteAvg();

        final Position position = getBusinessService().getPosition();
        final String entryPrice = String.format("short/long: %s/%s",
                position.getPriceAvgShort() != null ? position.getPriceAvgShort().toPlainString() : null,
                position.getPriceAvgLong() != null ? position.getPriceAvgLong().toPlainString() : null);

        return new AccountInfoJson(
                wallet.toPlainString(),
                available.toPlainString(),
                margin.toPlainString(),
                getPositionString(position),
                upl.toPlainString(),
                position.getLeverage().toPlainString(),
                getBusinessService().getAffordable().getForLong().toPlainString(),
                getBusinessService().getAffordable().getForShort().toPlainString(),
                quAvg.toPlainString(),
                liqPrice == null ? null : liqPrice.toPlainString(),
                eMark != null ? eMark.toPlainString() : "0",
                eLast != null ? eLast.toPlainString() : "0",
                eBest != null ? eBest.toPlainString() : "0",
                eAvg != null ? eAvg.toPlainString() : "0",
                entryPrice,
                accountInfoContracts.toString());
    }


    public AccountInfoJson getFullAccountInfo() {

        final FullBalance fullBalance = getBusinessService().calcFullBalance();
        if (fullBalance.getAccountInfoContracts() == null) {
            return new AccountInfoJson("error", "error", "error", "error", "error", "error", "error",
                    "error", "error", "error", "error", "error", "error", "error", "error", "error");
        }

        final AccountInfoContracts accountInfoContracts = fullBalance.getAccountInfoContracts();
        final Position position = fullBalance.getPosition();

        final BigDecimal available = accountInfoContracts.getAvailable();
        final BigDecimal wallet = accountInfoContracts.getWallet();
        final BigDecimal margin = accountInfoContracts.getMargin();
        final BigDecimal upl = accountInfoContracts.getUpl();
        final BigDecimal quAvg = getBusinessService().getArbitrageService().calcQuAvg();
        final BigDecimal liqPrice = position.getLiquidationPrice();
        final BigDecimal eMark = accountInfoContracts.geteMark();
        final BigDecimal eLast = accountInfoContracts.geteLast();
        final BigDecimal eBest = accountInfoContracts.geteBest();
        final BigDecimal eAvg = accountInfoContracts.geteAvg();

        final String entryPrice = String.format("long/short:%s/%s; %s",
                position.getPriceAvgLong() != null ? position.getPriceAvgLong().toPlainString() : null,
                position.getPriceAvgShort() != null ? position.getPriceAvgShort().toPlainString() : null,
                fullBalance.getTempValues());
        if (available == null || wallet == null || margin == null || upl == null
                || position.getLeverage() == null || quAvg == null) {
//            throw new IllegalStateException("Balance and/or Position are not yet defined. entryPrice=" + entryPrice);
            return new AccountInfoJson("error", "error", "error", "error", "error", "error", "error",
                    "error", "error", "error", "error", "error", "error", "error",
                    entryPrice, "error");
        }

        return new AccountInfoJson(
                wallet.toPlainString(),
                available.toPlainString(),
                margin.toPlainString(),
                getPositionString(position),
                upl.toPlainString(),
                position.getLeverage().toPlainString(),
                getBusinessService().getAffordable().getForLong().toPlainString(),
                getBusinessService().getAffordable().getForShort().toPlainString(),
                quAvg.toPlainString(),
                liqPrice == null ? null : liqPrice.toPlainString(),
                eMark != null ? eMark.toPlainString() : "0",
                eLast != null ? eLast.toPlainString() : "0",
                eBest != null ? eBest.toPlainString() : "0",
                eAvg != null ? eAvg.toPlainString() : "0",
                entryPrice,
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
        return getBusinessService().getAllOpenOrders().stream()
                .filter(o -> o.getOrder().getTradableAmount() != null)
                .filter(o -> o.getOrder().getTradableAmount().compareTo(BigDecimal.ZERO) != 0)
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
        final LiqParams liqParams = liqInfo.getLiqParams();
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
}
