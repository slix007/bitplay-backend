package com.bitplay.market;

import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.knowm.xchange.dto.trade.LimitOrder;

/**
 * Created by Sergey Shurmin on 04/27/19.
 */
public abstract class MarketServicePortions extends MarketService {

    final ConcurrentLinkedQueue<PlaceOrderArgs> portionsQueue = new ConcurrentLinkedQueue<>();

    public ConcurrentLinkedQueue<PlaceOrderArgs> getPortionsQueue() {
        return portionsQueue;
    }

    public String getPortionsProgressForUi() {
        final List<FplayOrder> onlyOpenFplayOrders = getOpenOrders().stream()
                        .filter(Objects::nonNull)
                        .filter(o -> o.getLimitOrder() != null)
                        .filter(FplayOrder::isOpen)
                        .collect(Collectors.toList());
        return onlyOpenFplayOrders.stream()
                .map(FplayOrder::getPortionsStr)
                .findFirst()
                .orElseGet(() -> {
                    final PlaceOrderArgs peek = portionsQueue.peek();
                    if (peek != null) {
                        return String.format("%s/%s", peek.getPortionsQty(), peek.getPortionsQtyMax());
                    }
                    return "0/0";
                });
    }

    public TradeResponseJson placeWithPortions(PlaceOrderArgs p, BigDecimal portionsQty) {

        if (portionsQty == null || portionsQty.compareTo(BigDecimal.ONE) <= 0) {
            final TradeResponse r = this.placeOrder(p);
            return new TradeResponseJson(r.getOrderId(), r.getErrorCode());
        }

        // define portions
        final BigDecimal amount = p.getAmount();
        if (amount.signum() <= 0) {
            return new TradeResponseJson("", "wrong amount " + amount);
        }
        final List<BigDecimal> parts = defineParts(portionsQty, amount);
        if (parts.size() == 0) {
            return new TradeResponseJson("0 parts", "wrong amount " + amount);
        }

        final PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                .orderType(p.getOrderType())
                .amount(parts.get(0))
                .placingType(p.getPlacingType())
                .signalType(p.getSignalType())
                .attempt(0)
                .tradeId(p.getTradeId())
                .counterName(p.getSignalType().getCounterName())
                .portionsQty(1)
                .portionsQtyMax(parts.size())
                .build();
        final TradeResponse r = this.placeOrder(placeOrderArgs);

        for (int i = 1; i < parts.size(); i++) {
            final BigDecimal part = parts.get(i);
            putIntoQueue(p, part, i, parts.size());
        }

        return new TradeResponseJson(r.getOrderId(), r.getErrorCode());
    }

    private void putIntoQueue(PlaceOrderArgs p, BigDecimal part, Integer i, int size) {
        final PlaceOrderArgs toPut = PlaceOrderArgs.builder()
                .orderType(p.getOrderType())
                .amount(part)
                .placingType(p.getPlacingType())
                .signalType(p.getSignalType())
                .attempt(0)
                .tradeId(p.getTradeId())
                .counterName(p.getSignalType().getCounterName())
                .portionsQty(i + 1)
                .portionsQtyMax(size)
                .build();
        this.getPortionsQueue().add(toPut);
    }

    private List<BigDecimal> defineParts(BigDecimal portionsQty, BigDecimal amount) {
        List<BigDecimal> placingParts = new ArrayList<>();
        BigDecimal part = amount.divide(portionsQty, 0, RoundingMode.DOWN);
        if (part.signum() == 0) {
            part = BigDecimal.ONE;
        }
        BigDecimal sum = BigDecimal.ZERO;
        while (sum.compareTo(amount) < 0) {
            if (sum.add(part).compareTo(amount) <= 0 && placingParts.size() != portionsQty.intValue()) {
                sum = sum.add(part);
                placingParts.add(part);
            } else {
                BigDecimal lastPart = amount.subtract(sum);
                final int lastInd = placingParts.size() - 1;
                if (lastInd >= 0) {
                    lastPart = lastPart.add(placingParts.get(lastInd));
                    placingParts.remove(lastInd);
                }
                sum = sum.add(lastPart);
                placingParts.add(lastPart);
            }
        }
        return placingParts;
    }

    public Integer cancelAllPortions() {
        final int inQueue = portionsQueue.size();
        portionsQueue.clear();
        final FplayOrder stub = new FplayOrder(getMarketId(), null, "CancelPortionsFromUI");
        final List<LimitOrder> cancelPortionsFromUI = cancelAllOrders(stub, "CancelPortionsFromUI", false, true);
        return inQueue + cancelPortionsFromUI.size();
    }

}
