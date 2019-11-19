package com.bitplay.persistance;

import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRepositoryService {

    private final OrderRepository orderRepository;

    public FplayOrder findOne(String id) {
        return orderRepository.findOne(id);
    }

    public List<FplayOrder> findAll(Long tradeId, Integer marketId) {
        return orderRepository.findAllByTradeIdAndMarketId(tradeId, marketId);
    }

    private FplayOrder updateTask(FplayOrder updated) {
//        final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
//        log.info("updateTask " + updated.getOrderId() + "; " + stackTrace[4]);
//        log.info("updateTask from "
//                + "\n " + stackTrace[3] // updateSync
//                + "\n " + stackTrace[4] //
//                + "\n " + stackTrace[5]
//        );
        final String orderId = updated.getOrderId();
        FplayOrder one = orderRepository.findOne(orderId);
        if (one == null) {
            return orderRepository.save(updated);
        }

        FplayOrderUtils.updateFplayOrderFields(one, updated);
        orderRepository.save(one);
        return one;
    }

    @SuppressWarnings("UnusedReturnValue")
    public FplayOrder updateSync(FplayOrder updated) {
        return updateWithOneRepeat(updated);
    }

    private FplayOrder updateWithOneRepeat(FplayOrder updated) {
        FplayOrder fplayOrder = null;
        try {
            try {
                fplayOrder = updateTask(updated);
            } catch (DuplicateKeyException | OptimisticLockingFailureException e) {
                log.error("order save error " + e.toString());
                fplayOrder = updateTask(updated);
            }
        } catch (DuplicateKeyException | OptimisticLockingFailureException e) {
            log.error("order save double error " + e.toString());
        } catch (Exception e) {
            log.error("order save double error", e);
        }
        return fplayOrder;
    }

}
