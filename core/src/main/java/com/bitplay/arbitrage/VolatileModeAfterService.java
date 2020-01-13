package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.posdiff.PosDiffPortionsStopListener;
import com.bitplay.market.MarketService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.PlBefore;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.CumPersistenceService;
import com.bitplay.persistance.DealPricesRepositoryService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.dealprices.FactPrice;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.settings.BitmexChangeOnSoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class VolatileModeAfterService {

    private final SettingsRepositoryService settingsRepositoryService;
    private final SignalService signalService;
    private final OkCoinService okexService;
    private final BitmexService bitmexService;
    private final BitmexChangeOnSoService bitmexChangeOnSoService;
    private final TradeService fplayTradeService;
    private final DealPricesRepositoryService dealPricesRepositoryService;
    private final CumPersistenceService cumPersistenceService;
    private final PosDiffPortionsStopListener posDiffPortionsStopListener;

    void justSetVolatileMode(Long tradeId, BtmFokAutoArgs btmFokAutoArgs, boolean btmCancelLogic) {
        final List<FplayOrder> bitmexOO = bitmexService.getOnlyOpenFplayOrdersClone();
        final List<FplayOrder> okexOO = okexService.getOnlyOpenFplayOrdersClone();

        // case 1. На обоих биржах выставлены лимитные ордера
        // case 3. На Bitmex System_overloaded, на Okex выставлен лимитный ордер.
//        if (bitmexOO.size() > 0 && okexOO.size() > 0) {
//            executorService.execute(() -> replaceLimitOrdersBitmex(bitmexOO));
//            executorService.execute(() -> replaceLimitOrdersOkex(okexOO));
//        }
        // case 2. На Bitmex выставлен лимитный ордер, Okex в статусе "Waiting Arb".
        // case 4. На Bitmex "System_overloaded", на Okex "Waiting_arb".
//        if (bitmexOO.size() > 0 && okexService.getMarketState() == MarketState.WAITING_ARB) {
//            executorService.execute(() -> replaceLimitOrdersBitmex(bitmexOO));
//             OK while VolatileMode has no 'Arbitrage version'
//        }

        final PlacingType okexPlacingType = settingsRepositoryService.getSettings().getOkexPlacingType();
        final PlaceOrderArgs updateArgs = okexService.changeDeferredPlacingType(okexPlacingType);
        if (updateArgs != null) {
            final String counterForLogs = updateArgs.getCounterNameWithPortion();
            fplayTradeService.setTradingMode(tradeId, TradingMode.CURRENT_VOLATILE);
            final String msg = String.format("#%s change Trading mode to current-volatile(okex has deferred)", counterForLogs);
            fplayTradeService.info(tradeId, counterForLogs, msg);
            okexService.getTradeLogger().info(msg);

            if (btmCancelLogic) {
                dealPricesRepositoryService.updateOkexPlacingType(tradeId, okexPlacingType);
            }
        }

        if (bitmexOO.size() > 0) {
            if (btmCancelLogic) {
                bitmexService.ooSingleExecutor.execute(() -> cancelLimitOrdersBitmex(bitmexOO, tradeId, updateArgs));
            } else {
                bitmexService.ooSingleExecutor.execute(() -> replaceLimitOrdersBitmex(bitmexOO, tradeId, btmFokAutoArgs));
            }
        }
        if (okexOO.size() > 0) {
            okexService.ooSingleExecutor.execute(() -> replaceLimitOrdersOkex(okexOO, tradeId));
        }
    }

    private void replaceLimitOrdersBitmex(List<FplayOrder> bitmexOO, Long tradeId, BtmFokAutoArgs btmFokAutoArgs) {
        while (bitmexService.getMarketState() == MarketState.SYSTEM_OVERLOADED) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error("Sleep error", e);
                return;
            }
            if (bitmexService.isMarketStopped()) {
                return;
            }
        }

        final PlacingType btmPlacingType = bitmexChangeOnSoService.getPlacingType();

        replaceLimitOrders(bitmexService, btmPlacingType, bitmexOO, tradeId, btmFokAutoArgs);
    }

    private void replaceLimitOrdersOkex(List<FplayOrder> okexOO, Long tradeId) {
        final PlacingType okexPlacingType = settingsRepositoryService.getSettings().getOkexPlacingType();
        replaceLimitOrders(okexService, okexPlacingType, okexOO, tradeId, null);
    }

    private void replaceLimitOrders(MarketService marketService, PlacingType placingType, List<FplayOrder> currOrders, Long tradeId,
                                    BtmFokAutoArgs btmFokAutoArgs) {
        if (placingType != PlacingType.MAKER && placingType != PlacingType.MAKER_TICK) {

            final MarketState prevState = marketService.getMarketState();
            // 1. cancel and set marketState=PLACING_ORDER
            final FplayOrder lastOO = marketService.getLastOO(currOrders);
            final FplayOrder stub = marketService.getCurrStub(tradeId, lastOO, currOrders);
            final String counterName = stub.getCounterName(); // no portions here
            final String counterForLogs = stub.getCounterWithPortion(); // no portions here
            final Integer portionsQty = lastOO != null ? lastOO.getPortionsQty() : null;
            if (counterForLogs == null) {
                final String warnStr = String.format("#%s WARNING counter is null!!!. orderToCancel=%s, stub=%s", counterForLogs, lastOO, stub);
                fplayTradeService.info(tradeId, null, warnStr);
                marketService.getTradeLogger().warn(warnStr);
                log.info(warnStr);
            }

            final List<LimitOrder> orders = marketService.cancelAllOrders(stub, "VolatileMode activated: CancelAllOpenOrders", true, true);
            final BigDecimal amountDiff = orders.stream()
                    .map(o -> {
                        final BigDecimal am = o.getTradableAmount().subtract(o.getCumulativeAmount());
                        final boolean sellType = o.getType() == OrderType.ASK || o.getType() == OrderType.EXIT_BID;
                        return sellType ? am.negate() : am;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 2. place
            if (amountDiff.signum() != 0) {

                fplayTradeService.setTradingMode(tradeId, TradingMode.CURRENT_VOLATILE);
                final String changeModeMsg = String.format("#%s change Trading mode to current-volatile(replacing order)", counterForLogs);
                fplayTradeService.info(tradeId, counterForLogs, changeModeMsg);
                marketService.getTradeLogger().warn(changeModeMsg);
                log.info(changeModeMsg);

                final OrderType orderType = amountDiff.signum() > 0 ? OrderType.BID : OrderType.ASK;
                final BigDecimal amountLeft = amountDiff.abs();
                BestQuotes bestQuotes = getBestQuotes(currOrders);

                if (marketService.getName().equals(BitmexService.NAME)) {
                    signalService.placeBitmexOrderOnSignal(orderType, amountLeft, bestQuotes, placingType, counterName, tradeId,
                            new PlBefore(), false,
                            btmFokAutoArgs);
                } else {
                    if (amountLeft.signum() <= 0 && okexService.hasDeferredOrders()) {
                        final String warnMsg = String.format("#%s current-volatile. amountLeft=%s and hasDeferredOrders", counterForLogs, amountLeft);
                        fplayTradeService.info(tradeId, counterForLogs, warnMsg);
                        marketService.getTradeLogger().warn(warnMsg);
                        log.info(warnMsg);

                        okexService.setMarketState(MarketState.WAITING_ARB);
                    } else {
                        final Settings s = settingsRepositoryService.getSettings();
                        signalService.placeOkexOrderOnSignal(orderType, amountLeft, bestQuotes, placingType, counterName, tradeId, false,
                                portionsQty, s.getArbScheme());
                    }
                }
            } else {
                final String msg = "VolatileMode activated: no amountLeft.";
                log.warn(msg);
                fplayTradeService.info(tradeId, counterForLogs, msg);
                marketService.getTradeLogger().warn(msg);
                marketService.setMarketState(prevState);
            }
        }
    }

    private BestQuotes getBestQuotes(List<FplayOrder> orders) {
        return orders.stream()
                .map(FplayOrder::getBestQuotes)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void cancelLimitOrdersBitmex(List<FplayOrder> bitmexOO, Long tradeId, PlaceOrderArgs currOkexDeferredArgs) {
        while (bitmexService.getMarketState() == MarketState.SYSTEM_OVERLOADED) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error("Sleep error", e);
                return;
            }
            if (bitmexService.isMarketStopped()) {
                return;
            }
        }

        final FplayOrder lastOO = bitmexService.getLastOO(bitmexOO);
        final FplayOrder stub = bitmexService.getCurrStub(tradeId, lastOO, bitmexOO);
        final String counterForLogs = stub.getCounterWithPortion(); // no portions here
        if (counterForLogs == null) {
            final String warnStr = String.format("#%s WARNING counter is null!!!. orderToCancel=%s, stub=%s", counterForLogs, lastOO, stub);
            fplayTradeService.info(tradeId, null, warnStr);
            bitmexService.getTradeLogger().warn(warnStr);
            log.info(warnStr);
        }

        bitmexService.cancelAllOrders(stub, "VolatileMode activated: CancelAllOpenOrders", false, false);
        // freeOoChecker should set READY state after

        final BigDecimal btmFilled = bitmexService.getBtmFilledAndUpdateBPriceFact(currOkexDeferredArgs, false);
        final FactPrice bPriceFact = dealPricesRepositoryService.getFullDealPrices(currOkexDeferredArgs.getTradeId()).getBPriceFact();
        if (btmFilled.compareTo(bPriceFact.getFullAmount()) < 0) { // not fully filled
            if (btmFilled.signum() == 0) {
                // unstarted
                final boolean cntUpdated = incAbortedOrUnstartedCounters(currOkexDeferredArgs, false);
                printSignalAborted(currOkexDeferredArgs, "unstarted", btmFilled, cntUpdated);

            } else {
                // aborted
                final boolean cntUpdated = incAbortedOrUnstartedCounters(currOkexDeferredArgs, true);
                printSignalAborted(currOkexDeferredArgs, "aborted", btmFilled, cntUpdated);
            }
        } else {
            // fully filled. Means bitmex was filled before 'cancel request'.
            // Do nothing.
        }

    }

    private void printSignalAborted(PlaceOrderArgs currArgs, String abortedOrUnstarted, BigDecimal btmFilled, boolean cntUpdated) {
        final String msg = String.format(
                "#%s VolatileMode activated signal %s btmFilled=%s %s_counter_updated=%s",
                currArgs.getCounterNameWithPortion(),
                abortedOrUnstarted,
                btmFilled,
                abortedOrUnstarted, cntUpdated);
        bitmexService.getTradeLogger().info(msg);
        fplayTradeService.info(currArgs.getTradeId(), currArgs.getCounterNameWithPortion(), msg);
        log.info(msg);
    }


    private boolean incAbortedOrUnstartedCounters(PlaceOrderArgs currArgs, boolean isAborted) {
        final Long tradeId = currArgs.getTradeId();
        final TradingMode tradingMode = dealPricesRepositoryService.getTradingMode(tradeId);
        if (tradingMode != null) {
            final boolean notAbortedOrUnstartedSignal = dealPricesRepositoryService.isNotAbortedOrUnstartedSignal(tradeId);
            if (notAbortedOrUnstartedSignal) {
                final DeltaName deltaName = currArgs.getDeltaName();
                if (isAborted) {
                    dealPricesRepositoryService.setAbortedSignal(tradeId);
                    if (deltaName == DeltaName.B_DELTA) {
                        cumPersistenceService.incAbortedSignalUnstartedVert1(tradingMode);
                    } else {
                        cumPersistenceService.incAbortedSignalUnstartedVert2(tradingMode);
                    }
                } else { //unstarted
                    dealPricesRepositoryService.setUnstartedSignal(tradeId);
                    if (deltaName == DeltaName.B_DELTA) {
                        cumPersistenceService.incUnstartedVert1(tradingMode);
                    } else {
                        cumPersistenceService.incUnstartedVert2(tradingMode);
                    }
                }
                return true;
            }
        }
        return false;
    }

}
