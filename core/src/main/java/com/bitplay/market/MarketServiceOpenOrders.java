package com.bitplay.market;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Sergey Shurmin on 12/24/17.
 */
public abstract class MarketServiceOpenOrders {

    protected final static Logger logger = LoggerFactory.getLogger(MarketServiceOpenOrders.class);
    protected static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    protected final Object openOrdersLock = new Object();
    protected volatile List<FplayOrder> openOrders = new ArrayList<>();
    protected Map<String, BestQuotes> orderIdToSignalInfo = new HashMap<>();

    public abstract Logger getTradeLogger();

    protected abstract void setFree(String... flags);

    protected abstract String getCounterName();

    public abstract PersistenceService getPersistenceService();

    public List<FplayOrder> getAllOpenOrders() {
        List<FplayOrder> orders;
        synchronized (openOrdersLock) {
            orders = openOrders == null
                    ? new ArrayList<>()
                    : openOrders.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return orders;
    }

    public List<LimitOrder> getOnlyOpenOrders() {
        List<LimitOrder> limitOrders;
        synchronized (openOrdersLock) {
            limitOrders = openOrders == null
                    ? new ArrayList<>()
                    : openOrders.stream()
                    .map(FplayOrder::getOrder)
                    .filter(Objects::nonNull)
                    .filter(order -> order.getStatus() == Order.OrderStatus.NEW
                            || order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED
                            || order.getStatus() == Order.OrderStatus.PENDING_NEW
                            || order.getStatus() == Order.OrderStatus.PENDING_CANCEL
                            || order.getStatus() == Order.OrderStatus.PENDING_REPLACE)
                    .map(LimitOrder.class::cast)
                    .collect(Collectors.toList());
        }
        return limitOrders;
    }

    public boolean hasOpenOrders() {
        boolean hasOO;
        synchronized (openOrdersLock) {

            validateOpenOrders(); // removes wrong

            hasOO = openOrders.stream()
                    .map(FplayOrder::getOrder)
                    .anyMatch(order -> order.getStatus() == Order.OrderStatus.NEW
                            || order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED
                            || order.getStatus() == Order.OrderStatus.PENDING_NEW
                            || order.getStatus() == Order.OrderStatus.PENDING_CANCEL
                            || order.getStatus() == Order.OrderStatus.PENDING_REPLACE
                    );

        }
        return hasOO;
    }

    protected void distinctOpenOrders() {
        Set<String> ordIdSet = new HashSet<>();
        openOrders = openOrders.stream().flatMap(fplayOrder -> {
            if (ordIdSet.add(fplayOrder.getOrderId())) {
                return Stream.of(fplayOrder);
            }
            logger.warn("OO is repeated " + fplayOrder.toString());
            return Stream.empty();
        }).collect(Collectors.toList());
    }

    private boolean validateOpenOrders() {
        long openOrdersCount;
        synchronized (openOrdersLock) {

            if (openOrders.stream().anyMatch(Objects::isNull)) {
                final String warnMsg = "WARNING: OO has null element";
                getTradeLogger().error(warnMsg);
                logger.error(warnMsg);
            }
            openOrders.stream()
                    .filter(Objects::nonNull)
                    .filter(limitOrder -> limitOrder.getOrder().getTradableAmount() == null)
                    .forEach(limitOrder -> {
                        final String warnMsg = "WARNING: OO amount is null. " + limitOrder.toString();
                        getTradeLogger().error(warnMsg);
                        logger.error(warnMsg);
                    });
            openOrders.removeIf(Objects::isNull);
            openOrders.removeIf(limitOrder -> limitOrder.getOrder().getTradableAmount() == null);

            openOrdersCount = openOrders.stream()
                    .filter(limitOrder -> limitOrder.getOrder().getTradableAmount().compareTo(BigDecimal.ZERO) != 0) // filter as for gui
                    .count();
            if (openOrders.size() != openOrdersCount) {
                final String message = "WARNING: OO with zero amount: " + openOrders.stream()
                        .map(FplayOrder::getOrder)
                        .map(Order::toString)
                        .reduce((s, s2) -> s + "; " + s2);
                getTradeLogger().warn(message);
                logger.warn(message);
            }
        } //synchronized (openOrdersLock)
        return openOrdersCount == 0;
    }

    protected void updateOpenOrders(List<LimitOrder> trades) {
        updateOpenOrders(trades, new FplayOrder(getCounterName()));
    }

    protected void updateOpenOrders(List<LimitOrder> trades, FplayOrder stabOrderForNew) {

        if (trades.size() == 0) {
            return;
        }

        trades.forEach(o -> getPersistenceService().getOrderRepositoryService().update(o, stabOrderForNew));

        synchronized (openOrdersLock) {

            // keep not-updated
//            StringBuilder updateAction = new StringBuilder("without update:");
            final List<FplayOrder> withoutUpdate = this.openOrders.stream()
                    .filter(existing -> trades.stream().noneMatch(update -> update.getId().equals(existing.getOrder().getId())))
//                    .peek(existing -> updateAction.append(",id=").append(existing.getOrder().getId()))
                    .collect(Collectors.toList());

            // new and updates (remove tooOldByTime only)
            this.openOrders = trades.stream()
                    .flatMap(update -> {

                        String counterName = getCounterName();
                        logger.info("{} Order update:id={},status={},amount={},filled={},time={}",
                                counterName,
                                update.getId(), update.getStatus(), update.getTradableAmount(),
                                update.getCumulativeAmount(),
                                update.getTimestamp().toString());

                        if (update.getStatus() == Order.OrderStatus.FILLED) {
                            getTradeLogger().info("{} Order {} FILLED", counterName, update.getId());
                        }

                        final FplayOrder fplayOrder = updateOpenOrder(update, stabOrderForNew); // exist or null

                        if (fplayOrder.getOrderId().equals("0")) {
                            getTradeLogger().warn("WARNING: update of fplayOrder with id=0: " + fplayOrder);
                        }

                        return removeOpenOrderByTime(fplayOrder);
                    }).collect(Collectors.toList());

            if (withoutUpdate.size() > 0) {
//                logger.info(updateAction.toString());
                this.openOrders.addAll(withoutUpdate);
            }

            // decrease orderIdToSignalInfo TODO improve this
            if (orderIdToSignalInfo.size() > 100) {
                logger.warn("orderIdToSignalInfo over 100");
                final Map<String, BestQuotes> newMap = new HashMap<>();
                openOrders.stream()
                        .map(FplayOrder::getOrder)
                        .map(Order::getId)
                        .filter(id -> orderIdToSignalInfo.containsKey(id))
                        .forEach(id -> newMap.put(id, orderIdToSignalInfo.get(id)));
                orderIdToSignalInfo = newMap;
            }

            // TODO
            if (!hasOpenOrders()) {
                logger.info("market-ready: " + trades.stream()
                        .map(LimitOrder::toString)
                        .collect(Collectors.joining("; ")));
                setFree();
            }
        } // synchronized (openOrdersLock)
    }

    private Stream<FplayOrder> removeOpenOrderByTime(FplayOrder fplayOrder) {
        Stream<FplayOrder> optionalOpenOrder = Stream.of(fplayOrder);
        final Order theOrder = fplayOrder.getOrder();
        if (isClosed(theOrder.getStatus())) {
            final long maxMs = 1000 * 30; // 30 sec
            final long nowMs = Instant.now().toEpochMilli();
            final Date orderTimestamp = theOrder.getTimestamp();
            if (orderTimestamp == null ||
                    nowMs - orderTimestamp.toInstant().toEpochMilli() > maxMs) {
                optionalOpenOrder = Stream.empty();

//                getTradeLogger().info("Remove order:id={},status={},amount={},filled={}",
//                        theOrder.getId(), theOrder.getStatus(), theOrder.getTradableAmount(),
//                        theOrder.getCumulativeAmount());
            }
        }
        return optionalOpenOrder;
    }

    private boolean isClosed(final Order.OrderStatus status) {
        return status == Order.OrderStatus.FILLED
                || status == Order.OrderStatus.REJECTED
                || status == Order.OrderStatus.CANCELED
                || status == Order.OrderStatus.EXPIRED
                || status == Order.OrderStatus.REPLACED
                || status == Order.OrderStatus.STOPPED;
    }

    /**
     * Use openOrdersLock.
     */
    private FplayOrder updateOpenOrder(LimitOrder update, FplayOrder stabOrderForNew) {
        final FplayOrder updated = this.openOrders.stream()
                .filter(fplayOrder -> fplayOrder.getOrderId().equals(update.getId()))
                .map(fplayOrder -> FplayOrderUtils.updateFplayOrder(fplayOrder, update))
                .findAny()
                .orElseGet(() -> FplayOrderUtils.updateFplayOrder(stabOrderForNew, update));
        return updated;
    }

    protected void cleanOldOO() {
        synchronized (openOrdersLock) {
            this.openOrders = this.openOrders.stream()
                    .flatMap(this::removeOpenOrderByTime)
                    .collect(Collectors.toList());
        }
    }

    protected void updateOOStatuses() {
        synchronized (openOrdersLock) {
            this.openOrders = this.openOrders.stream()
                    .flatMap(fplayOrder -> {
                        Stream<FplayOrder> optOrd;
                        try {
                            optOrd = Stream.of(updateOOStatus(fplayOrder));
                        } catch (Exception e) {
                            logger.error("Error on updateOOStatuses", e);
                            optOrd = Stream.of(fplayOrder);
                        }
                        return optOrd;
                    })
                    .collect(Collectors.toList());
        }
    }

    abstract protected Optional<Order> getOrderInfo(String orderId, String counterName, int attemptCount, String logInfoId, Logger logger);

    private FplayOrder updateOOStatus(FplayOrder fplayOrder) throws Exception {
        final String orderId = fplayOrder.getOrderId();
        final String counterName = getCounterName();
        final Optional<Order> orderInfoAttempts = getOrderInfo(orderId, counterName, 1, "updateOOStatus:", logger);

        if (!orderInfoAttempts.isPresent()) {
            throw new Exception("Failed to updateOOStatus id=" + orderId);
        }
        Order orderInfo = orderInfoAttempts.get();
        final LimitOrder limitOrder = (LimitOrder) orderInfo;

        if (fplayOrder.getOrder().getStatus() != Order.OrderStatus.FILLED && limitOrder.getStatus() == Order.OrderStatus.FILLED) {
            getTradeLogger().info("#{} updateOOStatus got FILLED orderId={}", getCounterName(), limitOrder.getId());
            logger.info("updateOOStatus got FILLED order: " + limitOrder.toString());
        }

        final FplayOrder updatedOrder = FplayOrderUtils.updateFplayOrder(fplayOrder, limitOrder);
        getPersistenceService().getOrderRepositoryService().update(limitOrder, updatedOrder);

        return updatedOrder;
    }

}
