package com.bitplay.persistance;

import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.repository.OrderRepository;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Service
public class OrderRepositoryService {

    private MongoOperations mongoOperation;

    private ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("order-repo-%d").build());

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    public OrderRepositoryService(MongoOperations mongoOperation) {
        this.mongoOperation = mongoOperation;
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

            final FplayOrder updated = FplayOrderUtils.updateFplayOrder(fplayOrder, movedLimitOrder);


            orderRepository.save(updated);
        });
    }

    public synchronized void update(LimitOrder update, FplayOrder stabOrderForNew) {
        executor.submit(() -> {

            final String orderId = update.getId();
            FplayOrder one = orderRepository.findOne(orderId);
            if (one == null) {
                one = stabOrderForNew;
            }
            if (one == null) {
                return null; // can not update. No
            }
            one = FplayOrderUtils.updateFplayOrder(one, update);

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
