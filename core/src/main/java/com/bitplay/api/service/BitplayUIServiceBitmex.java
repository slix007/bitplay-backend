package com.bitplay.api.service;

import com.bitplay.api.domain.ChangeRequestJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.api.domain.ob.FutureIndexJson;
import com.bitplay.api.domain.ob.LimitsJson;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.bitmex.BitmexFunding;
import com.bitplay.market.bitmex.BitmexLimitsService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.bitmex.BitmexTimeService;
import com.bitplay.persistance.domain.settings.AmountType;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import info.bitrich.xchangestream.bitmex.dto.BitmexContractIndex;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.trade.UserTrades;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Component("Bitmex")
public class BitplayUIServiceBitmex extends AbstractBitplayUIService<BitmexService> {

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okexService;

    @Autowired
    private BitmexTimeService bitmexTimeService;

    @Autowired
    private BitmexLimitsService bitmexLimitsService;

    @Override
    public BitmexService getBusinessService() {
        return bitmexService;
    }

    @Override
    public List<VisualTrade> fetchTrades() {
        final UserTrades trades = bitmexService.fetchMyTradeHistory();

        List<VisualTrade> askTrades = trades.getTrades().stream()
                .sorted((o1, o2) -> o1.getTimestamp().before(o2.getTimestamp()) ? 1 : -1)
                .map(this::toVisualTrade)
                .collect(Collectors.toList());
        return askTrades;
    }

    @Override
    protected String getPositionString(Position position) {
        return position.getPositionLong().signum() > 0
                ? "+" + position.getPositionLong().toPlainString()
                : position.getPositionLong().toPlainString();
    }

    public TradeResponseJson doTrade(TradeRequestJson tradeRequestJson) {
        final BigDecimal amount = new BigDecimal(tradeRequestJson.getAmount());
        Order.OrderType orderType;
        SignalType signalType;
        switch (tradeRequestJson.getType()) {
            case BUY:
                orderType = Order.OrderType.BID;
                signalType = SignalType.MANUAL_BUY;
                break;
            case SELL:
                orderType = Order.OrderType.ASK;
                signalType = SignalType.MANUAL_SELL;
                break;
            default:
                return new TradeResponseJson("Wrong orderType", "Wrong orderType");
//                throw new IllegalArgumentException("No such order type " + tradeRequestJson.getType());
        }
        final String toolName = tradeRequestJson.getToolName();
        final AmountType amountType = tradeRequestJson.getAmountType();

        final PlacingType placingType = PlacingType.valueOf(tradeRequestJson.getPlacementType().toString());
        final TradeResponse tradeResponse = bitmexService.singleOrder(orderType, amount, null, signalType, placingType, toolName, amountType);

        return new TradeResponseJson(tradeResponse.getOrderId(), tradeResponse.getErrorCode());
    }

    public FutureIndexJson getFutureIndex() {
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

        if (bitmexService.getPosition() == null || bitmexService.getPosition().getPositionLong() == null) {
            return FutureIndexJson.empty();
        }
        final String position = bitmexService.getPosition().getPositionLong().toPlainString();

        final String timeCompareString = bitmexTimeService.getTimeCompareString();
        final Integer timeCompareUpdating = bitmexTimeService.fetchTimeCompareUpdating();

        final LimitsJson limitsJson = bitmexLimitsService.getLimitsJson();

        final String bxbtBal = bitmexService.getContractType().isEth()
                ? ".BXBT: " + bitmexService.getBtcContractIndex().getIndexPrice()
                : "";

        // Index diff = b_index (n) - o_index (k) = x,
        final BigDecimal okexIndex = okexService.getContractIndex().getIndexPrice();
        final String twoMarketsIndexDiff = String.format("Index diff = b_index (%s) - o_index (%s) = %s",
                indexPrice.toPlainString(),
                okexIndex.toPlainString(),
                indexPrice.subtract(okexIndex).toPlainString()
        );

        return new FutureIndexJson(
                indexString,
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
                bxbtBal,
                twoMarketsIndexDiff);
    }

    public ResultJson setCustomSwapTime(ChangeRequestJson customSwapTime) {
        final String swapTime = customSwapTime.getCommand();

        bitmexService.getBitmexSwapService().setCustomSwapTime(swapTime);

        return new ResultJson("true", "");
    }

    public ResultJson resetTimeCompare() {
        final String timeCompareString = bitmexTimeService.resetTimeCompare();
        return new ResultJson(timeCompareString, "");
    }

    public ResultJson updateTimeCompareUpdating(ChangeRequestJson changeRequestJson) {
        final String command = changeRequestJson.getCommand();
        final Integer timeCompareUpdating = bitmexTimeService.updateTimeCompareUpdating(Integer.valueOf(command));
        return new ResultJson(String.valueOf(timeCompareUpdating), "");
    }

}
