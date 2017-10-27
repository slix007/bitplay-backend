package com.bitplay.api.service;

import com.bitplay.api.domain.ChangeRequestJson;
import com.bitplay.api.domain.FutureIndexJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.bitmex.BitmexFunding;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.bitmex.BitmexTimeService;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.domain.SwapParams;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.trade.UserTrades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Component("Bitmex")
public class BitplayUIServiceBitmex extends AbstractBitplayUIService<BitmexService> {

    private static final Logger logger = LoggerFactory.getLogger(BitplayUIServiceBitmex.class);

    @Autowired
    private BitmexService service;

    @Autowired
    private BitmexTimeService bitmexTimeService;

    @Override
    public BitmexService getBusinessService() {
        return service;
    }

    @Override
    public List<VisualTrade> fetchTrades() {
        final UserTrades trades = service.fetchMyTradeHistory();

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

        String orderId = null;
        if (tradeRequestJson.getPlacementType() == TradeRequestJson.PlacementType.TAKER) {
            final TradeResponse tradeResponse = service.takerOrder(orderType, amount, null, signalType);
            orderId = tradeResponse.getOrderId();
        } else if (tradeRequestJson.getPlacementType() == TradeRequestJson.PlacementType.MAKER) {

            final TradeResponse tradeResponse = service.placeOrderOnSignal(orderType, amount, null, signalType);
            orderId = tradeResponse.getOrderId();
        }

        return new TradeResponseJson(orderId, null);
    }

    @Override
    public FutureIndexJson getFutureIndex() {
        final FutureIndexJson futureIndexParent = super.getFutureIndex();

        final BitmexFunding bitmexFunding = service.getBitmexFunding();
        String fundingRate = bitmexFunding.getFundingRate() != null ? bitmexFunding.getFundingRate().toPlainString() : "";
        String fundingCost = service.getFundingCost() != null ? service.getFundingCost().toPlainString() : "";

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

        final String position = service.getPosition().getPositionLong().toPlainString();

        final String timeCompareString = bitmexTimeService.getTimeCompareString();

        return new FutureIndexJson(futureIndexParent.getIndex(), futureIndexParent.getTimestamp(),
                fundingRate,
                fundingCost,
                position,
                swapTime,
                timeToSwap,
                signalType,
                timeCompareString);
    }

    public ResultJson setCustomSwapTime(ChangeRequestJson customSwapTime) {
        final String swapTime = customSwapTime.getCommand();
        final String serviceName = service.getName();
        SwapParams swapParams = service.getPersistenceService().fetchSwapParams(serviceName);
        swapParams.setCustomSwapTime(swapTime);
        service.getPersistenceService().saveSwapParams(swapParams, serviceName);
        return new ResultJson("true", "");
    }
}
