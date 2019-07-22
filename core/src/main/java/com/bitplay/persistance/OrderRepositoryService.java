package com.bitplay.persistance;

import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.repository.OrderRepository;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Service
public class OrderRepositoryService {

    private MongoOperations mongoOperation;

    //    private ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("order-repo-%d").build());
    private Object lock = new Object();

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    public OrderRepositoryService(MongoOperations mongoOperation) {
        this.mongoOperation = mongoOperation;
    }

    public FplayOrder findOne(String id) {
        return orderRepository.findOne(id);
    }

    public void updateOrder(FplayOrder fplayOrder, LimitOrder movedLimitOrder) {
        if (fplayOrder.getOrder().getStatus() == Order.OrderStatus.CANCELED
                && movedLimitOrder.getStatus() != Order.OrderStatus.CANCELED) {
            // do nothing
            return;
        }

        final FplayOrder updated = FplayOrderUtils.updateFplayOrder(fplayOrder, movedLimitOrder);
        orderRepository.save(updated);
    }

    public synchronized void update(LimitOrder update, FplayOrder stabOrderForNew) {
        final String orderId = update.getId();
        FplayOrder one = orderRepository.findOne(orderId);
        if (one == null) {
            one = stabOrderForNew;
        }
        if (one == null) {
            return; // can not update.
        }
        one = FplayOrderUtils.updateFplayOrder(one, update);

        orderRepository.save(one);
    }

    public void save(FplayOrder fplayOrder) {
        orderRepository.save(fplayOrder);
    }

    public void save(Iterable<? extends FplayOrder> fplayOrders) {
        orderRepository.save(fplayOrders);
    }

    public synchronized Long findTradeId(List<LimitOrder> trades) {
        List<String> orderIds = trades.stream()
                .map(LimitOrder::getId)
                .collect(Collectors.toList());

        return mongoOperation.find(new Query(Criteria.where("_id").in(orderIds)), FplayOrder.class)
                .stream()
                .map(FplayOrder::getTradeId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null);
    }
}
