package com.bitplay.persistance.domain.fluent;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;

/**
 * Created by Sergey Shurmin on 1/4/18.
 */
public class FplayOrderUtils {

    public static FplayOrder updateFplayOrder(FplayOrder fplayOrder, LimitOrder update) {
        final FplayOrder updated;
        if (fplayOrder == null) {
            throw new IllegalArgumentException("FplayOrder stub for update is null");
        }

        if (fplayOrder.getOrderId() != null) {
            LimitOrder existing = (LimitOrder) fplayOrder.getOrder();

            final LimitOrder limitOrder = updateLimitOrder(existing, update);

            updated = new FplayOrder(fplayOrder.getCounterName(), limitOrder,
                    fplayOrder.getBestQuotes(), fplayOrder.getPlacingType(), fplayOrder.getSignalType());
        } else {
            updated = new FplayOrder(fplayOrder.getCounterName(), update, null, null, null);
        }
        return updated;
    }

    public static FplayOrder updateFplayOrder(FplayOrder exists, FplayOrder update) {
        final FplayOrder updated;

        if (exists != null && update != null) {
            final LimitOrder existingLimit = (LimitOrder) exists.getOrder();
            final LimitOrder updateLimit = (LimitOrder) update.getOrder();

            final LimitOrder limitOrder = updateLimitOrder(existingLimit, updateLimit);

            updated = new FplayOrder(exists.getCounterName(), limitOrder,
                    exists.getBestQuotes() != null ? exists.getBestQuotes() : update.getBestQuotes(),
                    exists.getPlacingType() != null ? exists.getPlacingType() : update.getPlacingType(),
                    exists.getSignalType() != null ? exists.getSignalType() : update.getSignalType());

        } else {
            updated = update;
        }

        return updated;
    }

    private static LimitOrder updateLimitOrder(LimitOrder existing, LimitOrder update) {
        // if updated status is older than new
        if ((existing.getStatus() == Order.OrderStatus.NEW && update.getStatus() == Order.OrderStatus.PENDING_NEW)
                || (existing.getStatus() == Order.OrderStatus.CANCELED && update.getStatus() != Order.OrderStatus.CANCELED)
                || (existing.getStatus() == Order.OrderStatus.FILLED && update.getStatus() != Order.OrderStatus.FILLED)
                ) {
            return existing;
        }

        return new LimitOrder(
                existing.getType(),
                update.getTradableAmount() != null ? update.getTradableAmount() : existing.getTradableAmount(),
                existing.getCurrencyPair(),
                existing.getId(),
                update.getTimestamp(),
                update.getLimitPrice() != null ? update.getLimitPrice() : existing.getLimitPrice(),
                update.getAveragePrice() != null ? update.getAveragePrice() : existing.getAveragePrice(),
                update.getCumulativeAmount() != null ? update.getCumulativeAmount() : existing.getCumulativeAmount(),
                update.getStatus() != null ? update.getStatus() : existing.getStatus());
    }

}
