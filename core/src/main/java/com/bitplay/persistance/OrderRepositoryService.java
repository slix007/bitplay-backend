package com.bitplay.persistance;

import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.repository.OrderRepository;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Service
public class OrderRepositoryService {

    private MongoOperations mongoOperation;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    public OrderRepositoryService(MongoOperations mongoOperation) {
        this.mongoOperation = mongoOperation;
    }

    public static FplayOrder updateFplayOrder(FplayOrder fplayOrder, LimitOrder update) {
        final FplayOrder updated;

        if (fplayOrder != null) {
            LimitOrder existing = (LimitOrder) fplayOrder.getOrder();
            if ((existing.getStatus() == Order.OrderStatus.CANCELED && update.getStatus() != Order.OrderStatus.CANCELED)
                    || (existing.getStatus() == Order.OrderStatus.FILLED && update.getStatus() != Order.OrderStatus.FILLED)) {
                updated = fplayOrder;
            } else {
                final LimitOrder limitOrder = new LimitOrder(
                        existing.getType(),
                        update.getTradableAmount() != null ? update.getTradableAmount() : existing.getTradableAmount(),
                        existing.getCurrencyPair(),
                        existing.getId(),
                        update.getTimestamp(),
                        update.getLimitPrice() != null ? update.getLimitPrice() : existing.getLimitPrice(),
                        update.getAveragePrice() != null ? update.getAveragePrice() : existing.getAveragePrice(),
                        update.getCumulativeAmount() != null ? update.getCumulativeAmount() : existing.getCumulativeAmount(),
                        update.getStatus() != null ? update.getStatus() : existing.getStatus());

                updated = new FplayOrder(limitOrder, fplayOrder.getBestQuotes(), fplayOrder.getPlacingType(), fplayOrder.getSignalType());
            }

        } else {
            updated = new FplayOrder(update, null, null, null);
        }
        return updated;
    }

    public synchronized FplayOrder findOne(String id) {
        return orderRepository.findOne(id);
    }

    public synchronized void updateOrder(FplayOrder fplayOrder, LimitOrder movedLimitOrder) {
        executor.submit(() -> {
            if (fplayOrder.getOrder().getStatus() == Order.OrderStatus.CANCELED
                    && movedLimitOrder.getStatus() != Order.OrderStatus.CANCELED) {
                // do nothing
                return;
            }

            final FplayOrder updated = updateFplayOrder(fplayOrder, movedLimitOrder);


            orderRepository.save(updated);
        });
    }

    public synchronized void update(LimitOrder update) {
        executor.submit(() -> {

            final String orderId = update.getId();
            FplayOrder one = orderRepository.findOne(orderId);

            one = updateFplayOrder(one, update);

            orderRepository.save(one);

            return one;
        });
    }

    public synchronized void save(FplayOrder fplayOrder) {
        executor.submit(() -> {
            orderRepository.save(fplayOrder);
        });
    }
}
