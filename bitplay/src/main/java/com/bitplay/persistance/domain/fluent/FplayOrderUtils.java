package com.bitplay.persistance.domain.fluent;

import com.bitplay.xchange.dto.Order;
import com.bitplay.xchange.dto.trade.LimitOrder;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 1/4/18.
 */
public class FplayOrderUtils {

    public static FplayOrder updateFplayOrder(FplayOrder fplayOrder, LimitOrder theUpdate) {
        final FplayOrder updated;
        if (fplayOrder == null) {
            throw new IllegalArgumentException("FplayOrder stub for update is null");
        }

        final LimitOrder updatedLimit = fplayOrder.getOrderId() != null
                ? updateLimitOrder(fplayOrder.getLimitOrder(), theUpdate)
                : theUpdate;

        updated = fplayOrder.cloneWithUpdate(updatedLimit);

        return updated;
    }

    public static FplayOrder updateFplayOrder(FplayOrder exists, FplayOrder update) {
        final FplayOrder updated;

        if (exists != null && update != null) {
            final LimitOrder existingLimit = (LimitOrder) exists.getOrder();
            final LimitOrder updateLimit = (LimitOrder) update.getOrder();

            final LimitOrder limitOrder = updateLimitOrder(existingLimit, updateLimit);

            updated = new FplayOrder( // use exists metadata over update metadata. Why? update may be a stub?
                    exists.getMarketId() != null ? exists.getMarketId() : update.getMarketId(),
                    exists.getTradeId() != null ? exists.getTradeId() : update.getTradeId(),
                    exists.getCounterName() != null ? exists.getCounterName() : update.getCounterName(),
                    limitOrder,
                    exists.getBestQuotes() != null ? exists.getBestQuotes() : update.getBestQuotes(),
                    exists.getPlacingType() != null ? exists.getPlacingType() : update.getPlacingType(),
                    exists.getSignalType() != null ? exists.getSignalType() : update.getSignalType(),
                    exists.getPortionsQty() != null ? exists.getPortionsQty() : update.getPortionsQty(),
                    exists.getPortionsQtyMax() != null ? exists.getPortionsQtyMax() : update.getPortionsQtyMax());

        } else {
            updated = update;
        }

        return updated;
    }

    public static void updateFplayOrderFields(FplayOrder exists, FplayOrder update) {

        if (update != null) {
            final LimitOrder existingLimit = (LimitOrder) exists.getOrder();
            final LimitOrder updateLimit = (LimitOrder) update.getOrder();

            final LimitOrder limitOrder = updateLimitOrder(existingLimit, updateLimit);

            exists.setMarketId(exists.getMarketId() != null ? exists.getMarketId() : update.getMarketId());
            exists.setTradeId(exists.getTradeId() != null ? exists.getTradeId() : update.getTradeId());
            exists.setCounterName(exists.getCounterName() != null ? exists.getCounterName() : update.getCounterName());
            exists.setOrderDetail(FplayOrderConverter.convert(limitOrder));
            exists.setBestQuotes(exists.getBestQuotes() != null ? exists.getBestQuotes() : update.getBestQuotes());
            exists.setPlacingType(exists.getPlacingType() != null ? exists.getPlacingType() : update.getPlacingType());
            exists.setSignalType(exists.getSignalType() != null ? exists.getSignalType() : update.getSignalType());
            exists.setPortionsQty(exists.getPortionsQty() != null ? exists.getPortionsQty() : update.getPortionsQty());
            exists.setPortionsQtyMax(exists.getPortionsQtyMax() != null ? exists.getPortionsQtyMax() : update.getPortionsQtyMax());
        }
    }

    private static LimitOrder updateLimitOrder(LimitOrder existing, LimitOrder update) {
        // if updated status is older than new
        if ((existing.getStatus() == Order.OrderStatus.NEW && update.getStatus() == Order.OrderStatus.PENDING_NEW)
                || (existing.getStatus() == Order.OrderStatus.CANCELED && update.getStatus() != Order.OrderStatus.CANCELED)
                || (existing.getStatus() == Order.OrderStatus.FILLED && update.getStatus() != Order.OrderStatus.FILLED)
                ) {
            return existing;
        }

        Date timestamp; // latest or new Date()
        if (update.getTimestamp() == null && existing.getTimestamp() == null) {
            timestamp = new Date();
        } else if (update.getTimestamp() == null) {
            timestamp = existing.getTimestamp();
        } else if (existing.getTimestamp() == null) {
            timestamp = update.getTimestamp();
        } else if (update.getTimestamp().after(existing.getTimestamp())) {
            timestamp = update.getTimestamp();
        } else {
            timestamp = existing.getTimestamp();
        }

        return new LimitOrder(
                existing.getType(),
                update.getTradableAmount() != null ? update.getTradableAmount() : existing.getTradableAmount(),
                existing.getCurrencyPair(),
                existing.getId(),
                timestamp,
                update.getLimitPrice() != null ? update.getLimitPrice() : existing.getLimitPrice(),
                update.getAveragePrice() != null ? update.getAveragePrice() : existing.getAveragePrice(),
                update.getCumulativeAmount() != null ? update.getCumulativeAmount() : existing.getCumulativeAmount(),
                update.getStatus() != null ? update.getStatus() : existing.getStatus());
    }

}
