package com.bitplay.persistance;

import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.repository.OrderRepository;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
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

    private ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("order-repo-%d").build());
//    private Object lock = new Object();

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    public OrderRepositoryService(MongoOperations mongoOperation) {
        this.mongoOperation = mongoOperation;
    }

    public FplayOrder findOne(String id) {
        return orderRepository.findOne(id);
    }

    public List<FplayOrder> findAll(Long tradeId, Integer marketId) {
        return orderRepository.findAllByTradeIdAndMarketId(tradeId, marketId);
    }

    private FplayOrder updateTask(FplayOrder updated) {
        try {
            final String orderId = updated.getOrderId();
            FplayOrder one = orderRepository.findOne(orderId);
            if (one == null) {
                return orderRepository.save(updated);
            }

            FplayOrderUtils.updateFplayOrderFields(one, updated);

            orderRepository.save(one);
            return one;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Future<FplayOrder> updateAsync(FplayOrder updated) {
        return executor.submit(() -> updateTask(updated));
    }

    public FplayOrder updateSync(FplayOrder updated) {
        return updateTask(updated);
    }


    public void updateAsync(Iterable<? extends FplayOrder> fplayOrders) {
        for (FplayOrder fplayOrder : fplayOrders) {
            executor.submit(() -> updateTask(fplayOrder));
        }
    }

    public Long findTradeId(List<LimitOrder> trades) {
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
