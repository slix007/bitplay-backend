package com.bitplay.api.service;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.market.LimitsService;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.bitmex.BitmexFunding;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.bitmex.BitmexTimeService;
import com.bitplay.market.model.FullBalance;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.api.dto.AccountInfoJson;
import com.bitplay.api.dto.LiquidationInfoJson;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.TickerJson;
import com.bitplay.api.dto.TradeRequestJson;
import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.api.dto.VisualTrade;
import com.bitplay.api.dto.ob.FutureIndexJson;
import com.bitplay.api.dto.ob.LimitsJson;
import com.bitplay.api.dto.ob.OrderBookJson;
import com.bitplay.api.dto.ob.OrderJson;
import com.bitplay.model.AccountBalance;
import com.bitplay.model.Pos;
import com.bitplay.model.SwapSettlement;
import com.bitplay.xchangestream.bitmex.dto.BitmexContractIndex;
import com.bitplay.utils.Utils;
import java.math.RoundingMode;
import com.bitplay.xchange.currency.Currency;
import com.bitplay.xchange.dto.Order;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.account.AccountInfo;
import com.bitplay.xchange.dto.account.Position;
import com.bitplay.xchange.dto.account.Wallet;
import com.bitplay.xchange.dto.marketdata.ContractIndex;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.marketdata.Ticker;
import com.bitplay.xchange.dto.marketdata.Trade;
import com.bitplay.xchange.dto.trade.ContractLimitOrder;
import com.bitplay.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bitplay.utils.Utils.timestampToStr;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
public abstract class AbstractUiService<T extends MarketService> {

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
                o.getTimestamp() != null ? timestampToStr(o.getTimestamp()) : null,
                amountInBtc,
                o.getCumulativeAmount() != null ? o.getCumulativeAmount().toPlainString() : ""
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
                o.getTimestamp() != null ? timestampToStr(o.getTimestamp()) : null,
                amountInBtc,
                o.getCumulativeAmount() != null ? o.getCumulativeAmount().toPlainString() : ""
        );
    };

    public abstract T getBusinessService();

    public BitmexTimeService getBitmexTimeService() {
        throw new IllegalStateException("no BitmexTimeService");
    }

    public FutureIndexJson getFutureIndex() {
        final T businessService = getBusinessService();
        if (businessService == null) {
            return FutureIndexJson.empty();
        }
        if (businessService.getMarketStaticData() == MarketStaticData.BITMEX) {
            return getFutureIndexBitmex(businessService);
        }
        if (businessService.getMarketStaticData() == MarketStaticData.OKEX) {
            return getFutureIndexOkex(businessService);
        }
        return FutureIndexJson.empty();
    }

    public FutureIndexJson getFutureIndexOkex(T businessService) {
        final OkCoinService service = (OkCoinService) businessService;
        final LimitsService okexLimitsService = service.getLimitsService();

        final ContractIndex contractIndex = getBusinessService().getContractIndex();
        final String indexVal = contractIndex.getIndexPrice().toPlainString();
        final BigDecimal markPrice = service.getMarkPrice();

        final String indexString = String.format("%s/%s (1c=%sbtc)",
                indexVal,
                markPrice,
                getBusinessService().calcBtcInContract());
        final Date timestamp = contractIndex.getTimestamp();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        final LimitsJson limitsJson = okexLimitsService.getLimitsJson();

        // bid[1] в token trading (okex spot).
        String ethBtcBal = "";
        if (service.getContractType().isQuanto()
                && service.getEthBtcTicker() != null
                && service.getContractType() instanceof OkexContractType) {
            OkexContractType ct = (OkexContractType) service.getContractType();
            ethBtcBal = String.format("Quote %s/BTC: %s",
                    ct.getBaseTool(),
                    service.getEthBtcTicker().getBid().toPlainString());
        }

        final String okexEstimatedDeliveryPrice = service.getForecastPrice().toPlainString();
        final SwapSettlement swapSettlement = service.getSwapSettlement();
        return new FutureIndexJson(indexString, indexVal, sdf.format(timestamp), limitsJson, ethBtcBal, okexEstimatedDeliveryPrice, swapSettlement,
                service.getArbType());

    }

    public FutureIndexJson getFutureIndexBitmex(T businessService) {
        final ArbitrageService arbitrageService = businessService.getArbitrageService();
        BitmexService bitmexService = (BitmexService) businessService;
        final MarketServicePreliq right = arbitrageService.getRightMarketService();

        final BitmexContractIndex contractIndex;
        try {
            contractIndex = (BitmexContractIndex) bitmexService.getContractIndex();
        } catch (ClassCastException e) {
            return FutureIndexJson.empty();
        }

        final BigDecimal indexPrice = contractIndex.getIndexPrice();
        final BigDecimal markPrice = contractIndex.getMarkPrice();
        final String indexString = String.format("%s/%s (1c=%sbtc)",
                indexPrice.toPlainString(),
                markPrice.toPlainString(),
                getBusinessService().calcBtcInContract());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final String timestamp = sdf.format(contractIndex.getTimestamp());

        final BitmexFunding bitmexFunding = bitmexService.getBitmexSwapService().getBitmexFunding();
        String fundingRate = bitmexFunding.getFundingRate() != null ? bitmexFunding.getFundingRate().toPlainString() : "";
        String fundingCost = bitmexService.getFundingCost() != null ? bitmexService.getFundingCost().toPlainString() : "";

        String swapTime = "";
        String timeToSwap = "";
        if (bitmexFunding.getSwapTime() != null && bitmexFunding.getUpdatingTime() != null) {
            swapTime = bitmexFunding.getSwapTime().format(DateTimeFormatter.ISO_TIME);

            final Instant updateInstant = bitmexFunding.getUpdatingTime().toInstant();
            final Instant swapInstant = bitmexFunding.getSwapTime().toInstant();
            final long s = Duration.between(updateInstant, swapInstant).getSeconds();
            timeToSwap = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
        }

        String signalType = "noSwap";
        if (bitmexFunding.getSignalType() == null) {
            signalType = "null";
        } else if (bitmexFunding.getSignalType() != SignalType.SWAP_NONE) {
            signalType = bitmexFunding.getSignalType().name();
        }

        if (bitmexService.getPos() == null || bitmexService.getPos().getPositionLong() == null) {
            return FutureIndexJson.empty();
        }
        final String position = bitmexService.getPosVal().toPlainString();

        final String timeCompareString = getBitmexTimeService().getTimeCompareString();
        final Integer timeCompareUpdating = getBitmexTimeService().fetchTimeCompareUpdating();

        final LimitsJson limitsJson = arbitrageService.getLeftMarketService().getLimitsService().getLimitsJson();

        final String bxbtBal = bitmexService.getContractType().isQuanto()
                ? ".BXBT: " + bitmexService.getBtcContractIndex().getIndexPrice().setScale(2, RoundingMode.HALF_UP)
                : "";

        return new FutureIndexJson(
                indexString,
                indexPrice.toPlainString(),
                timestamp,
                fundingRate,
                fundingCost,
                position,
                swapTime,
                timeToSwap,
                signalType,
                timeCompareString,
                String.valueOf(timeCompareUpdating),
                limitsJson,
                bxbtBal);
    }


    VisualTrade toVisualTrade(Trade trade) {
        return new VisualTrade(
                trade.getCurrencyPair().toString(),
                trade.getPrice().toPlainString(),
                trade.getTradableAmount().toPlainString(),
                trade.getType().toString(),
                timestampToStr(trade.getTimestamp())
        );
    }

    public OrderBookJson getOrderBook() {
        if (getBusinessService() == null) {
            return new OrderBookJson();
        }
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
        if (getBusinessService() == null) {
            return new AccountInfoJson();
        }

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
                    entryPrice, "error", "error", "error");
        }

        final String ethBtcBid1 = getBusinessService().getEthBtcTicker() != null ? getBusinessService().getEthBtcTicker().getBid().toPlainString() : null;

        final BigDecimal longAvailToClose = position.getLongAvailToClose() != null ? position.getLongAvailToClose() : BigDecimal.ZERO;
        final BigDecimal shortAvailToClose = position.getShortAvailToClose() != null ? position.getShortAvailToClose() : BigDecimal.ZERO;
        final String plPos = position.getPlPos() != null ? position.getPlPos().toPlainString() : "";
        final String plPosBest = position.getPlPosBest() != null ? position.getPlPosBest().toPlainString() : "";
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
                plPos, plPosBest);
    }

    protected String getPositionString(final Pos position) {
        return position.getPositionLong().signum() >= 0
                ? "+" + position.getPositionLong().toPlainString()
                : position.getPositionLong().toPlainString();
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
        final T businessService = getBusinessService();
        if (businessService == null) {
            return new ArrayList<>();
        }
        final List<OrderJson> res = businessService.getOpenOrders().stream()
                .map(openOrderToJson)
                .collect(Collectors.toList());
        final PlaceOrderArgs da = businessService.getDeferredOrder();
        if (da != null) {
            final String currency;
            if (da.getContractType() == null) {
                currency = "";
            } else {
                currency = businessService.getPersistenceService().getSettingsRepositoryService()
                        .getCurrencyPair(businessService.getContractType()).toString();
            }
            res.add(new OrderJson(da.getCounterName(), "DEFERRED", "WAITING", currency, "",
                    da.getAmount().toPlainString(),
                    da.getOrderType() != null ? da.getOrderType().toString() : "null",
                    "", "", ""
            ));
        }
        return res;

    }

    public ResultJson moveOpenOrder(OrderJson orderJson) {
        final String id = orderJson.getId();
        final MoveResponse response = getBusinessService().moveMakerOrderFromGui(id);
        return new ResultJson(response.getMoveOrderStatus().toString(), response.getDescription());
    }

    public LiquidationInfoJson getLiquidationInfoJson() {
        final T bs = getBusinessService();
        if (bs == null) {
            return new LiquidationInfoJson("", "", "", "", "");
        }
        final LiqInfo liqInfo = bs.getLiqInfo();
        final LiqParams liqParams = bs.getPersistenceService().fetchLiqParams(bs.getNameWithType());
        String dqlString = liqInfo.getDqlString();
        final String s = bs.getArbType().s();
        if (dqlString != null && dqlString.startsWith(s + "_DQL = na")) {
            dqlString = s + "_DQL = na";
        }
        return new LiquidationInfoJson(
                liqInfo.getDqlCurr() != null ? liqInfo.getDqlCurr().toString() : "",
                dqlString,
                liqInfo.getDmrlString(),
                String.format("DQL: %s ... %s", liqParams.getDqlMin(), liqParams.getDqlMax()),
                String.format("DMRL: %s ... %s", liqParams.getDmrlMin(), liqParams.getDmrlMax()),
                liqInfo.getDqlStringExtra(),
                String.format("DQL_extra: %s ... %s", liqParams.getDqlMinExtra(), liqParams.getDqlMaxExtra())
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
