package com.bitplay.api.service;

import com.bitplay.api.domain.FutureIndexJson;
import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.TradeResponse;

import info.bitrich.xchangestream.bitmex.dto.BitmexInstrument;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.trade.UserTrades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Component("Bitmex")
public class BitplayUIServiceBitmex extends AbstractBitplayUIService<BitmexService> {

    private static final Logger logger = LoggerFactory.getLogger(BitplayUIServiceBitmex.class);

    @Autowired
    BitmexService service;

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

        String fundingRate = "";
        String timeToFunding = "";

        final ContractIndex contractIndex1 = getBusinessService().getContractIndex();
        if (contractIndex1 instanceof BitmexInstrument) {
            final BitmexInstrument contractIndex = (BitmexInstrument) contractIndex1;
            fundingRate = contractIndex.getFundingRate().toPlainString();

            if (contractIndex.getFundingTimestamp() != null) {
                final Instant timestamp = contractIndex.getTimestamp().toInstant();
                final Instant funding = contractIndex.getFundingTimestamp().toInstant();
                final long s = Duration.between(timestamp, funding).getSeconds();
                timeToFunding = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
            }
        }

        return new FutureIndexJson(futureIndexParent.getIndex(), futureIndexParent.getTimestamp(), fundingRate, timeToFunding);
    }
}
