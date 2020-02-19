package com.bitplay.api.service;

import com.bitplay.api.dto.ChangeRequestJson;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.TradeRequestJson;
import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.api.dto.ob.FutureIndexJson;
import com.bitplay.api.dto.ob.LimitsJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.bitmex.BitmexFunding;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.bitmex.BitmexTimeService;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.model.Pos;
import com.bitplay.persistance.domain.settings.PlacingType;
import info.bitrich.xchangestream.bitmex.dto.BitmexContractIndex;
import lombok.RequiredArgsConstructor;
import org.knowm.xchange.dto.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Component("Bitmex")
@RequiredArgsConstructor
public class BitplayUIServiceBitmex extends AbstractBitplayUIService<MarketServicePreliq> {

    private final ArbitrageService arbitrageService;

    @Autowired
    private BitmexTimeService bitmexTimeService;

    @Override
    public MarketServicePreliq getBusinessService() {
        return arbitrageService.getLeftMarketService();
    }

    @Override
    protected String getPositionString(Pos position) {
        return position.getPositionLong().signum() > 0
                ? "+" + position.getPositionLong().toPlainString()
                : position.getPositionLong().toPlainString();
    }

    @SuppressWarnings("Duplicates")
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

        final PlacingType placingType = PlacingType.valueOf(tradeRequestJson.getPlacementType().toString());

        final Long tradeId = arbitrageService.getLastInProgressTradeId();
        final String toolName = tradeRequestJson.getToolName();
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        if (left.getMarketStaticData() == MarketStaticData.BITMEX && toolName != null && toolName.equals("XBTUSD")) {
            final String counterName = left.getCounterName(signalType, tradeId);
            final PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                    .orderType(orderType)
                    .amount(amount)
                    .placingType(placingType)
                    .signalType(signalType)
                    .attempt(1)
                    .tradeId(tradeId)
                    .counterName(counterName)
                    .contractType(BitmexService.bitmexContractTypeXBTUSD)
                    .amountType(tradeRequestJson.getAmountType())
                    .build();
            final TradeResponse tradeResponse = left.placeOrder(placeOrderArgs);
            return new TradeResponseJson(tradeResponse.getOrderId(), tradeResponse.getErrorCode());
        }
        // Portions for mainSet orders
        final PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                .orderType(orderType)
                .amount(amount)
                .placingType(placingType)
                .signalType(signalType)
                .attempt(1)
                .tradeId(tradeId)
                .counterName(signalType.getCounterName())
                .build();

        return left.placeWithPortions(placeOrderArgs, tradeRequestJson.getPortionsQty());
    }

    public FutureIndexJson getFutureIndex() {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        if (left.getMarketStaticData() != MarketStaticData.BITMEX) {
            return FutureIndexJson.empty();
        }
        BitmexService bitmexService = (BitmexService) left;
        OkCoinService okexService = (OkCoinService) arbitrageService.getRightMarketService();

        final BitmexContractIndex contractIndex;
        try {
            contractIndex = (BitmexContractIndex) left.getContractIndex();
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
        final String position = bitmexService.getPos().getPositionLong().toPlainString();

        final String timeCompareString = bitmexTimeService.getTimeCompareString();
        final Integer timeCompareUpdating = bitmexTimeService.fetchTimeCompareUpdating();

        final LimitsJson limitsJson = arbitrageService.getLeftMarketService().getLimitsService().getLimitsJson();

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
                bxbtBal,
                twoMarketsIndexDiff);
    }

    public ResultJson setCustomSwapTime(ChangeRequestJson customSwapTime) {
        final String swapTime = customSwapTime.getCommand();

        if (arbitrageService.getLeftMarketService().getMarketStaticData() == MarketStaticData.BITMEX) {
            ((BitmexService) arbitrageService.getLeftMarketService()).getBitmexSwapService().setCustomSwapTime(swapTime);
        }

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
