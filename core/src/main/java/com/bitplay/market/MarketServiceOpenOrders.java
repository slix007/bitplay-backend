package com.bitplay.market;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 12/24/17.
 */
public abstract class MarketServiceOpenOrders {

    protected Logger log = LoggerFactory.getLogger(MarketService.class);
    protected LogService tradeLogger = new DefaultLogService();
    protected LogService defaultLogger = new DefaultLogService(log);
    protected static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    //    private final Object ooLock = new Object();
    private volatile CopyOnWriteArrayList<FplayOrder> openOrders = new CopyOnWriteArrayList<>();
    protected Map<String, BestQuotes> orderIdToSignalInfo = new HashMap<>();

    public LogService getTradeLogger() {
        return tradeLogger;
    }

    public LogService getLogger() {
        return defaultLogger;
    }

    protected abstract void setFree(Long tradeId, String... flags);

    protected abstract FplayOrder getCurrStub();

    protected abstract String getCounterName();

    public abstract PersistenceService getPersistenceService();

    private OrderRepositoryService getOrderRepositoryService() {
        return getPersistenceService().getOrderRepositoryService();
    }

    public abstract ArbitrageService getArbitrageService();

    public synchronized List<FplayOrder> getOpenOrders() {
        return this.openOrders;
    }

    public synchronized List<FplayOrder> getOpenOrdersClone() {
        return this.openOrders.stream()
                .map(FplayOrder::cloneDeep)
                .collect(Collectors.toList());
    }

    public synchronized List<FplayOrder> getOnlyOpenFplayOrdersClone() {
        return this.openOrders.stream()
                .filter(FplayOrder::isOpen)
                .map(FplayOrder::cloneDeep)
                .collect(Collectors.toList());
    }

    public synchronized List<LimitOrder> getOnlyOpenOrders() {
        return this.openOrders.stream()
                .filter(FplayOrder::isOpen)
                .map(FplayOrder::getLimitOrder)
                .collect(Collectors.toList());
    }

    public List<FplayOrder> getOnlyOpenFplayOrders() {
        return this.openOrders.stream()
                .filter(FplayOrder::isOpen)
                .collect(Collectors.toList());
    }

    public boolean hasOpenOrdersNoBlock() {
        if (Thread.holdsLock(this)) {
            log.warn("hasOpenOrdersNoBlock... but something holdsBlock");
        }
        return this.openOrders.stream().anyMatch(FplayOrder::isOpen);
    }

    public synchronized boolean hasOpenOrders() {
        boolean hasOO;
//        validateForDuplicatesOO(); // just to logs
//        synchronized (ooLock) {
            hasOO = this.openOrders.stream().anyMatch(FplayOrder::isOpen);
//        }
        return hasOO;
    }

    public Long tryFindLastTradeId() {
        return getArbitrageService().getLastTradeId();
    }

    private void validateForDuplicatesOO() {
        Map<String, FplayOrder> map = new HashMap<>();
        this.openOrders.forEach(fplayOrder -> {
            String key = fplayOrder.getOrderId();
            if (map.containsKey(key)) {
                FplayOrder first = map.get(key);
                FplayOrder merged = FplayOrderUtils.updateFplayOrder(first, fplayOrder);
                log.warn("OO is repeated first " + first.toString());
                log.warn("OO is repeated second " + fplayOrder.toString());
                log.warn("OO is repeated merged " + merged.toString());
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

    protected synchronized void updateFplayOrders(List<FplayOrder> updates) {
//        synchronized (ooLock) {

            // 1. Merge updates with this.openOrders and from DB => save to DB
            final ArrayList<FplayOrder> allUpdated = new ArrayList<>();
            for (FplayOrder u : updates) {
                if (u.getOrderId() == null) {
                    log.warn("ORDER_ID IS NULL " + u);
                    continue;
                }
                FplayOrder res = u;
                final FplayOrder exists = this.openOrders.stream()
                        .filter(curr -> u.getOrderId().equals(curr.getOrderId()))
                        .findFirst()
                        .orElseGet(() -> getOrderRepositoryService().findOne(u.getOrderId()));
                if (exists != null) {
                    res = FplayOrderUtils.updateFplayOrder(exists, u);
                }
//                getOrderRepositoryService().save(res); // TODO all orders from this.openOrders should be in DB.
                getOrderRepositoryService().updateSync(res);
                allUpdated.add(res);
            }

            // 2. put into this.openOrders

            // a)replace existing
            final List<String> updatedIds = new ArrayList<>();
            this.openOrders.replaceAll(curr ->
                    allUpdated.stream()
                            .filter(u -> u.getOrderId().equals(curr.getOrderId()))
                            .findFirst()
                            .map(u -> {
                                updatedIds.add(u.getOrderId());
                                return u; //FplayOrderUtils.updateFplayOrder(curr, u);
                            })
                            .orElse(curr));
            // b)add new
            this.openOrders.addAll(allUpdated.stream()
                    .filter(o -> !updatedIds.contains(o.getOrderId()))
                    .collect(Collectors.toList())
            );

//        } // ooLock
    }

    protected void updateFplayOrdersToCurrStab(List<LimitOrder> updates, final FplayOrder currStub) {
        final List<FplayOrder> fplayOrderList = updates.stream()
                .map(currStub::cloneWithUpdate)
                .collect(Collectors.toList());
        updateFplayOrders(fplayOrderList);
    }

    protected synchronized Integer getOpenOrdersSize() {
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

    protected synchronized void cleanOldOO() {
//        synchronized (ooLock) {
            if (this.openOrders.size() > 0) {
                this.openOrders.removeIf(this::isOpenOrderToRemove);
            }
//        }
    }

    private boolean isOpenOrderToRemove(FplayOrder fplayOrder) {
        final Order theOrder = fplayOrder.getOrder();
        if (isClosed(theOrder.getStatus())) {
            final long maxMs = 1000 * 30; // 30 sec
            final long nowMs = Instant.now().toEpochMilli();
            final Date orderTimestamp = theOrder.getTimestamp();
            if (orderTimestamp == null) {
                log.warn("orderTimestamp is null." + fplayOrder);
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
                        log.error("updateOOStatus error", e);
                    }
                    return fplayOrder.cloneDeep();
                }).collect(Collectors.toList());

        updateFplayOrders(updatedOO);
    }

    abstract protected Optional<Order> getOrderInfo(String orderId, String counterName, int attemptCount, String logInfoId, LogService logger);
    abstract protected FplayOrder updateOOStatus(FplayOrder fplayOrder) throws Exception;

}
