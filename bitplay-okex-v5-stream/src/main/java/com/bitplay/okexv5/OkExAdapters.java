package com.bitplay.okexv5;

import com.bitplay.core.dto.PositionStream;
import com.bitplay.okexv5.dto.marketdata.OkCoinDepth;
import com.bitplay.okexv5.dto.marketdata.OkcoinIndexTicker;
import com.bitplay.okexv5.dto.marketdata.OkcoinTicker;
import com.bitplay.okexv5.dto.privatedata.OkExPosition;
import com.bitplay.okexv5.dto.privatedata.OkExUserInfoResult;
import com.bitplay.okexv5.dto.privatedata.OkExUserInfoResult.BalanceInfo;
import com.bitplay.okexv5.dto.privatedata.OkexAccountResult;
import com.bitplay.okexv5.dto.privatedata.OkexPos;
import com.bitplay.okexv5.dto.privatedata.OkexStreamOrder;
import com.bitplay.okexv5.dto.privatedata.OkexSwapPosition;
import com.bitplay.xchange.currency.Currency;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderStatus;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.marketdata.Ticker;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.okcoin.OkCoinAdapters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OkExAdapters {

    public OkExAdapters() {
    }


    public static AccountInfoContracts adaptAccountInfo(Currency baseTool, OkexAccountResult acc) {
        final String ccy = acc.getCcy();
        if (ccy != null && ccy.length() > 2 && ccy.equals(baseTool.getCurrencyCode())) { // BTC, ETH
            BigDecimal equity = acc.getEq() == null ? null :
                    acc.getEq().setScale(8, RoundingMode.HALF_UP);
            BigDecimal margin = acc.getFrozenBal() == null ? null :
                    acc.getFrozenBal().setScale(8, RoundingMode.HALF_UP);
            BigDecimal upl = acc.getUpl() == null ? null :
                    acc.getUpl().setScale(8, RoundingMode.HALF_UP);
            BigDecimal wallet = equity == null || upl == null ? null :
                    equity.subtract(upl).setScale(8, RoundingMode.HALF_UP);
            BigDecimal available = acc.getAvailEq() == null ? null :
                    acc.getAvailEq().setScale(8, RoundingMode.HALF_UP);
            BigDecimal rpl = acc.getLiab() == null ? BigDecimal.ZERO :
                    acc.getLiab().setScale(8, RoundingMode.HALF_UP);
            BigDecimal riskRate = acc.getMgnRatio() == null ? BigDecimal.ZERO
                    : acc.getMgnRatio().setScale(8, RoundingMode.HALF_UP);
            return new AccountInfoContracts(wallet, available, (BigDecimal) null, equity, (BigDecimal) null, (BigDecimal) null, margin, upl, rpl, riskRate);

        }
        throw new IllegalArgumentException("can not adaptSwapUserInfo: " + acc);
    }

    public static AccountInfoContracts adaptUserInfo(Currency baseTool, OkExUserInfoResult okExUserInfoResult) {
        BalanceInfo btcInfo;
        switch (baseTool.getCurrencyCode()) {
            case "BTC":
                btcInfo = okExUserInfoResult.getBtcInfo();
                break;
            case "ETH":
                btcInfo = okExUserInfoResult.getEthInfo();
                break;
            default:
                throw new IllegalArgumentException("Unsuported baseTool " + baseTool);
        }

        BigDecimal equity = btcInfo.getEquity().setScale(8, 4);
        BigDecimal margin = btcInfo.getMargin().setScale(8, 4);
        BigDecimal upl = btcInfo.getUnrealizedPnl().setScale(8, 4);
        BigDecimal wallet = equity.subtract(upl).setScale(8, 4);
        BigDecimal available = btcInfo.getTotalAvailBalance().setScale(8, 4);
//        BigDecimal available = equity.subtract(margin).setScale(8, 4);
        BigDecimal rpl = btcInfo.getRealizedPnl().setScale(8, 4);
        BigDecimal riskRate = btcInfo.getMarginRatio().setScale(8, 4);
        return new AccountInfoContracts(wallet, available, (BigDecimal) null, equity, (BigDecimal) null, (BigDecimal) null, margin, upl, rpl, riskRate);
    }

    public static OrderBook adaptOrderBook(OkCoinDepth depth, CurrencyPair currencyPair) {
        List<LimitOrder> asks = adaptLimitOrders(OrderType.ASK, depth.getAsks(), currencyPair, depth.getTimestamp());
//        Collections.reverse(asks);
        List<LimitOrder> bids = adaptLimitOrders(OrderType.BID, depth.getBids(), currencyPair, depth.getTimestamp());
        return new OrderBook(depth.getTimestamp(), depth.getReceiveTimestamp(), asks, bids);
    }

    private static List<LimitOrder> adaptLimitOrders(OrderType type, BigDecimal[][] list, CurrencyPair currencyPair, Date timestamp) {
        List<LimitOrder> limitOrders = new ArrayList<>(list.length);
        for (int i = 0; i < list.length; ++i) {
            BigDecimal[] data = list[i];
            limitOrders.add(adaptLimitOrder(type, data, currencyPair, (String) null, timestamp));
        }

        return limitOrders;
    }

    private static LimitOrder adaptLimitOrder(OrderType type, BigDecimal[] data, CurrencyPair currencyPair, String id, Date timestamp) {
        //[411.8,6,8,4][double ,int ,int ,int]
        // 411.8 is the price,
        // 6 is the size of the price,
        // 8 is the number of force-liquidated orders,
        // 4 is the number of orders of the price，
        // timestamp is the timestamp of the orderbook.
        BigDecimal tradableAmount = data[1].setScale(0, 4);
        LimitOrder contractLimitOrder = new LimitOrder(type, tradableAmount, currencyPair, id, timestamp, data[0]);
//        contractLimitOrder.setAmountInBaseCurrency(data[2]);
        return contractLimitOrder;
    }

    public static Ticker adaptTicker(OkcoinTicker okCoinTicker, CurrencyPair currencyPair) {
        return (new Ticker.Builder())
                .instId(okCoinTicker.getInstrumentId())
                .currencyPair(currencyPair)
                .high(null)
                .low(null)
                .bid(okCoinTicker.getBestBid() != null ? okCoinTicker.getBestBid().setScale(8, RoundingMode.HALF_UP) : null)
                .ask(okCoinTicker.getBestAsk())
                .last(okCoinTicker.getLast())
                .volume(okCoinTicker.getVolume24h())
                .timestamp(Date.from(okCoinTicker.getTimestamp())).build();

    }

    public static Ticker adaptIndexTicker(OkcoinIndexTicker okCoinTicker, CurrencyPair currencyPair) {
        return (new Ticker.Builder())
                .instId(okCoinTicker.getInstrumentId())
                .currencyPair(currencyPair)
                .high(null)
                .low(null)
                .bid(okCoinTicker.getBestBid())
                .ask(okCoinTicker.getBestAsk())
                .last(okCoinTicker.getLast())
                .volume(okCoinTicker.getVolume24h())
                .timestamp(Date.from(okCoinTicker.getTimestamp())).build();

    }
//
//    private static LimitOrder adaptLimitOrder(OrderType type, BigDecimal[] data, CurrencyPair currencyPair, String id, Date timestamp) {
//        BigDecimal tradableAmount = data[1].setScale(0, 4);
//        ContractLimitOrder contractLimitOrder = new ContractLimitOrder(type, tradableAmount, currencyPair, id, timestamp, data[0]);
//        contractLimitOrder.setAmountInBaseCurrency(data[2]);
//        return contractLimitOrder;
//    }

    private static OrderType convertType(String orderType) {
        if (orderType == null) {
            return null;
        }
        // Type (1: open long 2: open short 3: close long 4: close short)
        switch (orderType) {
            case "buy"://OPEN_LONG:
                return OrderType.BID;
            case "sell"://OPEN_SHORT:
                return OrderType.ASK;
//            case "3"://CLOSE_LONG:
//                return OrderType.EXIT_BID;
//            case "4"://CLOSE_SHORT:
//                return OrderType.EXIT_ASK;
            default:
                throw new IllegalArgumentException("enum is wrong");
        }
    }

    public static List<LimitOrder> adaptTradeResult(OkexStreamOrder[] okexStreamOrders) {
        List<LimitOrder> res = new ArrayList<>();
        for (OkexStreamOrder okexStreamOrder : okexStreamOrders) {

            final OrderType orderType = OkCoinAdapters.convertType(okexStreamOrder.getSide());
            final OrderStatus orderStatus = OkCoinAdapters.convertStatus(okexStreamOrder.getState());

            CurrencyPair currencyPair = parseCurrencyPair(okexStreamOrder.getInstrumentId());
            final LimitOrder limitOrder = new LimitOrder(orderType,
                    okexStreamOrder.getSize(),
                    currencyPair,
                    okexStreamOrder.getOrderId(),
                    okexStreamOrder.getTimestamp(),
                    okexStreamOrder.getPrice(),
                    okexStreamOrder.getPriceAvg(),
                    okexStreamOrder.getFilledQty(),
//                    okExUserOrder.getFee(),
                    orderStatus);
            res.add(limitOrder);
        }
        return res;
    }

    private static CurrencyPair parseCurrencyPair(String instrumentId) { // instrumentId BTC-USD-170317
        final String[] split = instrumentId.split("-");
        final String base = split[0];
        final String counter = split[1];
        return new CurrencyPair(Currency.getInstance(base), Currency.getInstance(counter));
    }

    public static PositionStream adaptPosition(OkExPosition p) {
        return new PositionStream(
                p.getLongQty(),
                p.getShortQty(),
                p.getLongAvailQty(),
                p.getShortAvailQty(),
                p.getLeverage(),
                p.getLiquidationPrice(),
                p.getLongAvgCost(),
                p.getShortAvgCost(),
                BigDecimal.ZERO, //mark value
                p.getInstrumentId(),
                p.getUpdatedAt().toInstant(),
                p.toString(),
                p.getLongPnl().add(p.getShortPnl())
        );

    }

    public static PositionStream adaptPos(OkexPos p) {
        return new PositionStream(
                p.getPos(),
                BigDecimal.ZERO,
                p.getAvailPos(),
                BigDecimal.ZERO,
                p.getLever(),
                p.getLiqPx(),
                p.getAvgPx(),
                BigDecimal.ZERO,
                BigDecimal.ZERO, //mark value
                p.getInstId(),
                p.getTimestamp(),
                p.toString(),
                null);
    }

    public static PositionStream adaptSwapPosition(OkexSwapPosition p) {
        final boolean aLong = p.getSide().equals("long");
        if (aLong) {
            return new PositionStream(
                    p.getPosition(),
                    null,
                    p.getAvail_position(),
                    null,
                    p.getLeverage(),
                    p.getLiquidation_price(),
                    p.getAvg_cost(),
                    null,
                    BigDecimal.ZERO, //mark value
                    p.getInstrument_id(),
                    p.getTimestamp(),
                    p.toString(),
                    p.getUnrealized_pnl());
        }
        // else short
        return new PositionStream(
                null,
                p.getPosition(),
                null,
                p.getAvail_position(),
                p.getLeverage(),
                p.getLiquidation_price(),
                null,
                p.getAvg_cost(),
                BigDecimal.ZERO, //mark value
                p.getInstrument_id(),
                p.getTimestamp(),
                p.toString(),
                p.getUnrealized_pnl());
    }

}
