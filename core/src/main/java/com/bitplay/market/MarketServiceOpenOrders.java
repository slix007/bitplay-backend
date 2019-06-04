package com.bitplay.market;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Sergey Shurmin on 12/24/17.
 */
public abstract class MarketServiceOpenOrders {

    private final static Logger logger = LoggerFactory.getLogger(MarketServiceOpenOrders.class);
    protected static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final Object ooLock = new Object();
    private volatile CopyOnWriteArrayList<FplayOrder> openOrders = new CopyOnWriteArrayList<>();
    protected Map<String, BestQuotes> orderIdToSignalInfo = new HashMap<>();

    public abstract LogService getTradeLogger();
    public abstract LogService getLogger();

    protected abstract void setFree(Long tradeId, String... flags);

    protected abstract void setFreeIfNoOrders(Long lastTradeId, List<LimitOrder> orders);

    protected abstract FplayOrder getCurrStub();

    protected abstract String getCounterName();

    public abstract PersistenceService getPersistenceService();

    public List<FplayOrder> getOpenOrders() {
        return openOrders;
    }

    public List<FplayOrder> getOpenOrdersClone() {
        return this.openOrders.stream()
                .map(FplayOrder::cloneDeep)
                .collect(Collectors.toList());
    }

    public List<LimitOrder> getOnlyOpenOrders() {
        return openOrders.stream()
                .filter(FplayOrder::isOpen)
                .map(FplayOrder::getLimitOrder)
                .collect(Collectors.toList());
    }

    public List<FplayOrder> getOnlyOpenFplayOrders() {
        return openOrders.stream()
                .filter(FplayOrder::isOpen)
                .collect(Collectors.toList());
    }

    public boolean hasOpenOrders() {
        boolean hasOO;
        validateForDuplicatesOO(); // just to logs

        hasOO = openOrders.stream()
                .anyMatch(FplayOrder::isOpen);
        return hasOO;
    }

    public Long tryFindLastTradeId() {
        Long lastTradeId = null;
        OptionalLong aLong = openOrders.stream()
                .map(FplayOrder::getTradeId)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue).max();
        if (aLong.isPresent()) {
            lastTradeId = aLong.getAsLong();
        }
        return lastTradeId;
    }

    private void validateForDuplicatesOO() {
        Map<String, FplayOrder> map = new HashMap<>();
        openOrders.forEach(fplayOrder -> {
            String key = fplayOrder.getOrderId();
            if (map.containsKey(key)) {
                FplayOrder first = map.get(key);
                FplayOrder merged = FplayOrderUtils.updateFplayOrder(first, fplayOrder);
                logger.warn("OO is repeated first " + first.toString());
                logger.warn("OO is repeated second " + fplayOrder.toString());
                logger.warn("OO is repeated merged " + merged.toString());
                map.put(key, merged);
            } else {
                map.put(key, fplayOrder);
            }
        });
    }

    /**
     * Adds or ...<br>
     *
     * If exists, updates all fplayOrder metadata and updates with general rules LimitOrder. // if the OO-subscription was first.
     */
    protected void addOpenOrder(FplayOrder fplayOrder) {
        updateFplayOrders(Collections.singletonList(fplayOrder));
    }

    protected void addOpenOrders(List<LimitOrder> trades, FplayOrder stubOrderForNew) {
        updateFplayOrdersToCurrStab(trades, stubOrderForNew);
    }

    protected void updateFplayOrders(List<FplayOrder> updates) {
        synchronized (ooLock) {
            // 1. update existing
            final List<String> updatedIds = new ArrayList<>();
            this.openOrders.replaceAll(curr ->
                    updates.stream()
                            .filter(u -> u.getOrderId().equals(curr.getOrderId()))
                            .findFirst()
                            .map(u -> {
                                updatedIds.add(u.getOrderId());
                                return FplayOrderUtils.updateFplayOrder(curr, u);
                            })
                            .orElse(curr));
            // 2. add new
            this.openOrders.addAll(updates.stream()
                    .filter(o -> !updatedIds.contains(o.getOrderId()))
                    .collect(Collectors.toList())
            );
        }
    }

    protected void updateFplayOrdersToCurrStab(List<LimitOrder> updates, final FplayOrder currStub) {
        final List<FplayOrder> fplayOrderList = updates.stream()
                .map(currStub::cloneWithUpdate)
                .collect(Collectors.toList());
        updateFplayOrders(fplayOrderList);
    }

    protected Integer getOpenOrdersSize() {
        return this.openOrders.size();
    }

    private boolean isClosed(final Order.OrderStatus status) {
        return status == Order.OrderStatus.FILLED
                || status == Order.OrderStatus.REJECTED
                || status == Order.OrderStatus.CANCELED
                || status == Order.OrderStatus.EXPIRED
                || status == Order.OrderStatus.REPLACED
                || status == Order.OrderStatus.STOPPED;
    }

    protected void cleanOldOO() {
        synchronized (ooLock) {
            if (openOrders.size() > 0) {
                this.openOrders.removeIf(this::isOpenOrderToRemove);
            }
        }
    }

    private boolean isOpenOrderToRemove(FplayOrder fplayOrder) {
        final Order theOrder = fplayOrder.getOrder();
        if (isClosed(theOrder.getStatus())) {
            final long maxMs = 1000 * 30; // 30 sec
            final long nowMs = Instant.now().toEpochMilli();
            final Date orderTimestamp = theOrder.getTimestamp();
            if (orderTimestamp == null) {
                logger.warn("orderTimestamp is null." + fplayOrder);
            }
            if (orderTimestamp == null || nowMs - orderTimestamp.toInstant().toEpochMilli() > maxMs) {
                return true; // remove the old
            }
        }
        return false;
    }

    protected void updateOOStatuses() {
        final List<FplayOrder> updatedOO = getOpenOrdersClone().stream()
                .map(fplayOrder -> {
                    try {
                        return updateOOStatus(fplayOrder);
                    } catch (Exception e) {
                        logger.error("updateOOStatus error", e);
                    }
                    return fplayOrder.cloneDeep();
                }).collect(Collectors.toList());

        updateFplayOrders(updatedOO);
    }

    abstract protected Optional<Order> getOrderInfo(String orderId, String counterName, int attemptCount, String logInfoId, LogService logger);

    /**
     * requests to market.
     */
    private FplayOrder updateOOStatus(FplayOrder fplayOrder) throws Exception {
        if (fplayOrder.getOrder().getStatus() == OrderStatus.CANCELED) {
            return fplayOrder;
        }

        final String orderId = fplayOrder.getOrderId();
        final String counterForLogs = fplayOrder.getTradeId() + ":" + getCounterName();
        final Optional<Order> orderInfoAttempts = getOrderInfo(orderId, counterForLogs, 1, "updateOOStatus:", getLogger());

        if (!orderInfoAttempts.isPresent()) {
            throw new Exception("Failed to updateOOStatus id=" + orderId);
        }
        Order orderInfo = orderInfoAttempts.get();
        final LimitOrder limitOrder = (LimitOrder) orderInfo;

        if (fplayOrder.getOrder().getStatus() != Order.OrderStatus.FILLED && limitOrder.getStatus() == Order.OrderStatus.FILLED) {
            getTradeLogger().info(String.format("#%s updateOOStatus got FILLED orderId=%s, avgPrice=%s, filledAm=%s", counterForLogs, limitOrder.getId(),
                    limitOrder.getAveragePrice(), limitOrder.getCumulativeAmount()));
            logger.info("#{} updateOOStatus got FILLED order: {}", counterForLogs, limitOrder.toString());
        }

        final FplayOrder updatedOrder = FplayOrderUtils.updateFplayOrder(fplayOrder, limitOrder);
        getPersistenceService().getOrderRepositoryService().update(limitOrder, updatedOrder);

        return updatedOrder;
    }

}
