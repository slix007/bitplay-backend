package com.bitplay.market;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.utils.Utils;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
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

    protected final Object openOrdersLock = new Object();
    protected volatile List<FplayOrder> openOrders = new ArrayList<>();
    protected Map<String, BestQuotes> orderIdToSignalInfo = new HashMap<>();

    public abstract LogService getTradeLogger();
    public abstract LogService getLogger();

    protected abstract void setFree(Long tradeId, String... flags);

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
                            .filter(Objects::nonNull)
                            .filter(FplayOrder::isOpen)
                            .map(FplayOrder::getLimitOrder)
                            .collect(Collectors.toList());
        }
        return limitOrders;
    }

    public List<FplayOrder> getOnlyOpenFplayOrders() {
        List<FplayOrder> orderList;
        synchronized (openOrdersLock) {
            orderList = openOrders == null
                    ? new ArrayList<>()
                    : openOrders.stream()
                            .filter(Objects::nonNull)
                            .filter(o -> o.getLimitOrder() != null)
                            .filter(FplayOrder::isOpen)
                            .collect(Collectors.toList());
        }
        return orderList;
    }

    public boolean hasOpenOrders() {
        boolean hasOO;
        synchronized (openOrdersLock) {

            validateOpenOrders(); // removes wrong

            hasOO = openOrders.stream()
                    .anyMatch(FplayOrder::isOpen);
        }
        return hasOO;
    }

    public Long tryFindLastTradeId() {
        Long lastTradeId = null;
        synchronized (openOrdersLock) {
            OptionalLong aLong = openOrders.stream()
                    .map(FplayOrder::getTradeId)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue).max();
            if (aLong.isPresent()) {
                lastTradeId = aLong.getAsLong();
            }
        }
        return lastTradeId;
    }

    protected void distinctOpenOrders() {
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
        openOrders = new ArrayList<>(map.values());
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

    /**
     * WARNING: the reason of orders with id==0, if used with OpenOrders subscriptions.<br>
     * <b> Use only when trades has id</b>
     */
//    protected void updateOrAddOpenOrder(LimitOrder fullInfoOrder, String counterName) {
//        FplayOrder stabOrderForNew = new FplayOrder(counterName); //
//        updateOpenOrders(Collections.singletonList(fullInfoOrder), stabOrderForNew); // getCounterName???
//    }

    /**
     * Adds or ...<br>
     *
     * If exists, updates all fplayOrder metadata and updates with general rules LimitOrder. // if the OO-subscription was first.
     */
    protected void addOpenOrder(FplayOrder fplayOrder) {
        synchronized (openOrdersLock) {
            updateOpenOrders(Collections.singletonList(fplayOrder.getLimitOrder()), fplayOrder);
        }
    }

    protected void addOpenOrders(List<LimitOrder> trades, FplayOrder stubOrderForNew) {
        updateOpenOrders(trades, stubOrderForNew);
    }

    protected void updateOpenOrder(LimitOrder trade) {
        updateOpenOrders(Collections.singletonList(trade), null);
    }

    protected void updateOpenOrders(List<LimitOrder> trades) {
        updateOpenOrders(trades, null);
    }

    /**
     * WARNING:<br> stubOrderForNew should be with correctly filled 'counterName' or null.
     *
     * @param limitOrderUpdates any orderInfo updates from server.
     * @param stubOrderForNew with correctly filled 'counterName' or null.
     */
    protected void updateOpenOrders(List<LimitOrder> limitOrderUpdates, FplayOrder stubOrderForNew) { // TODO

        if (limitOrderUpdates.size() == 0) {
            return;
        }

        synchronized (openOrdersLock) {

            // no new meta-info in fplayOrders, only new limitOrders
            Long tradeId = this.openOrders.stream()
                    .map(FplayOrder::getTradeId)
                    .reduce(null, Utils::lastTradeId);

            // keep not-updated
//            StringBuilder updateAction = new StringBuilder("without update:");
            final List<FplayOrder> withoutUpdate = this.openOrders.stream()
                    .filter(existing -> limitOrderUpdates.stream().noneMatch(update -> update.getId().equals(existing.getOrder().getId())))
//                    .peek(existing -> updateAction.append(",id=").append(existing.getOrder().getId()))
                    .collect(Collectors.toList());

            // new and updates (remove tooOldByTime only)
            this.openOrders = limitOrderUpdates.stream()
                    .flatMap(update -> {

                        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                        final String counterForLogs = getCounterName();
                        logger.info("#{} Order update:id={},status={},amount={},filled={},time={}",
                                counterForLogs,
                                update.getId(), update.getStatus(), update.getTradableAmount(),
                                update.getCumulativeAmount(),
                                df.format(update.getTimestamp()));

                        if (update.getStatus() == Order.OrderStatus.FILLED) {
                            getTradeLogger().info(String.format("#%s Order %s FILLED", counterForLogs, update.getId()));
                        }

                        final FplayOrder fplayOrder = updateOpenOrder(update, stubOrderForNew); // exist or null
                        if (fplayOrder == null) {
                            return Stream.empty();
                        }

                        getPersistenceService().getOrderRepositoryService().save(fplayOrder);

                        final LimitOrder updated = fplayOrder.getLimitOrder();
                        logger.info("#{} Order updated:id={},status={},amount={},filled={},time={}, placingType={}",
                                counterForLogs,
                                updated.getId(), updated.getStatus(), updated.getTradableAmount(),
                                updated.getCumulativeAmount(),
                                df.format(updated.getTimestamp()),
                                fplayOrder.getPlacingType());

                        if (fplayOrder.getOrderId().equals("0")) {
                            getTradeLogger().warn(String.format("#%s WARNING: update of fplayOrder with id=0: %s", counterForLogs, fplayOrder));
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

            // WORKAROUND1: use DB FplayOrder to find tradeId
            if (tradeId == null) {
                logger.warn("warning tradeId==null. OO:" + this.openOrders.stream()
                        .map(FplayOrder::toString)
                        .reduce((s, s2) -> s + ";;;" + s2));
                tradeId = getPersistenceService().getOrderRepositoryService().findTradeId(limitOrderUpdates);
            }

            // WORKAROUND2: use FplayTrades
//            if (tradeId == null) {
//                logger.warn("warning tradeId==null. OO:" + this.openOrders.stream()
//                        .map(FplayOrder::toString)
//                        .reduce((s, s2) -> s + ";;;" + s2));
//                tradeId = getPersistenceService().getOrderRepositoryService().findTradeId(limitOrderUpdates);
//                logger.warn("found tradeId==" + tradeId);
//            }


            // TODO
            if (!hasOpenOrders()) {
                logger.info("market-ready: " +
                        " tradeId=" + tradeId +
                        " limitOrders=" + limitOrderUpdates.stream()
                        .map(LimitOrder::toString)
                        .collect(Collectors.joining("; ")));
                setFree(tradeId);
            }
        } // synchronized (openOrdersLock)
    }


    /**
     * Use openOrdersLock.
     * <br>
     * <br
     * >
     *
     * @param mainFplayInfo not null
     * @param update not null
     * @return not null
     */
    private FplayOrder updateOpenOrder(@NotNull FplayOrder mainFplayInfo, @NotNull LimitOrder update) {
        final FplayOrder updated = this.openOrders.stream()
                .filter(fplayOrder -> fplayOrder.getOrderId().equals(update.getId()))
                .map(fplayOrder -> FplayOrderUtils.updateFplayOrder(fplayOrder, update))
                .findAny()
                .orElseGet(() -> FplayOrderUtils.updateFplayOrder(mainFplayInfo, update));
        return updated;
    }

    /**
     * Use openOrdersLock.
     * <br>
     * <br>
     * Can return null, if (FplayOrder not found && stab == null).
     */
    private FplayOrder updateOpenOrder(LimitOrder update, FplayOrder stabOrderForNew) {
        if (stabOrderForNew != null) {
            FplayOrder theUpdate = stabOrderForNew.cloneWithUpdate(update);

            return this.openOrders.stream()
                    .filter(fplayOrder -> fplayOrder.getOrderId().equals(update.getId()))
                    .map(fplayOrder -> FplayOrderUtils.updateFplayOrder(fplayOrder, theUpdate))
                    .findAny()
                    .orElse(theUpdate);
        } else {
            return this.openOrders.stream()
                    .filter(fplayOrder -> fplayOrder.getOrderId().equals(update.getId()))
                    .map(fplayOrder -> FplayOrderUtils.updateFplayOrder(fplayOrder, update))
                    .findAny()
                    .orElse(null);
        }
    }

    private Stream<FplayOrder> removeOpenOrderByTime(FplayOrder fplayOrder) {
        Stream<FplayOrder> optionalOpenOrder = Stream.of(fplayOrder);
        final Order theOrder = fplayOrder.getOrder();
        if (isClosed(theOrder.getStatus())) {
            final long maxMs = 1000 * 30; // 30 sec
            final long nowMs = Instant.now().toEpochMilli();
            final Date orderTimestamp = theOrder.getTimestamp();
            if (orderTimestamp == null) {
                logger.warn("orderTimestamp is null." + fplayOrder);
            }
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

    abstract protected Optional<Order> getOrderInfo(String orderId, String counterName, int attemptCount, String logInfoId, LogService logger);

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

//        if (fplayOrder.getOrder().getStatus() != Order.OrderStatus.FILLED && limitOrder.getStatus() == Order.OrderStatus.FILLED) {
//            getTradeLogger().info(String.format("#%s updateOOStatus got FILLED orderId=%s, avgPrice=%s, filledAm=%s", counterForLogs, limitOrder.getId(),
//                    limitOrder.getAveragePrice(), limitOrder.getCumulativeAmount()));
//            logger.info("#{} updateOOStatus got FILLED order: {}", counterForLogs, limitOrder.toString());
//        }

        final FplayOrder updatedOrder = FplayOrderUtils.updateFplayOrder(fplayOrder, limitOrder);
        getPersistenceService().getOrderRepositoryService().update(limitOrder, updatedOrder);

        return updatedOrder;
    }

}
