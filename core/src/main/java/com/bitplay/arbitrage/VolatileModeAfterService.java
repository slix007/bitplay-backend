package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.posdiff.PosDiffPortionsStopListener;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlBefore;
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
    private final ArbitrageService arbitrageService;
    private final BitmexChangeOnSoService bitmexChangeOnSoService;
    private final TradeService fplayTradeService;
    private final DealPricesRepositoryService dealPricesRepositoryService;
    private final CumPersistenceService cumPersistenceService;
    private final PosDiffPortionsStopListener posDiffPortionsStopListener;

    void justSetVolatileMode(Long tradeId, BtmFokAutoArgs btmFokAutoArgs, boolean btmCancelLogic) {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final MarketServicePreliq right = arbitrageService.getRightMarketService();
        final List<FplayOrder> leftOO = left.getOnlyOpenFplayOrdersClone();
        final List<FplayOrder> rightOO = right.getOnlyOpenFplayOrdersClone();

        // case 1. На обоих биржах выставлены лимитные ордера
        // case 3. На Bitmex System_overloaded, на Okex выставлен лимитный ордер.
//        if (leftOO.size() > 0 && rightOO.size() > 0) {
//            executorService.execute(() -> replaceLimitOrdersBitmex(leftOO));
//            executorService.execute(() -> replaceLimitOrdersOkex(rightOO));
//        }
        // case 2. На Bitmex выставлен лимитный ордер, Okex в статусе "Waiting Arb".
        // case 4. На Bitmex "System_overloaded", на Okex "Waiting_arb".
//        if (leftOO.size() > 0 && okexService.getMarketState() == MarketState.WAITING_ARB) {
//            executorService.execute(() -> replaceLimitOrdersBitmex(leftOO));
//             OK while VolatileMode has no 'Arbitrage version'
//        }

        final PlacingType okexPlacingType = settingsRepositoryService.getSettings().getOkexPlacingType();
        final PlaceOrderArgs updateArgs;
        if (right.getMarketStaticData() == MarketStaticData.OKEX) {
            final OkCoinService okexService = (OkCoinService) right;
            updateArgs = okexService.changeDeferredPlacingType(okexPlacingType);
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
        } else {
            updateArgs = null;
        }

        if (leftOO.size() > 0) {
            if (btmCancelLogic && updateArgs != null) {
                left.ooSingleExecutor.execute(() -> cancelLimitOrdersLeft(leftOO, tradeId, updateArgs));
            } else {
                left.ooSingleExecutor.execute(() -> replaceLimitOrdersLeft(leftOO, tradeId, btmFokAutoArgs));
            }
        }
        if (rightOO.size() > 0) {
            right.ooSingleExecutor.execute(() -> replaceLimitOrdersRight(rightOO, tradeId));
        }
    }

    private void replaceLimitOrdersLeft(List<FplayOrder> bitmexOO, Long tradeId, BtmFokAutoArgs btmFokAutoArgs) {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        if (bitmexSleepWhileSo(left)) return;

        final PlacingType btmPlacingType = bitmexChangeOnSoService.getPlacingType();

        replaceLimitOrders(left, btmPlacingType, bitmexOO, tradeId, btmFokAutoArgs);
    }

    private boolean bitmexSleepWhileSo(MarketServicePreliq left) {
        if (left.getMarketStaticData() == MarketStaticData.BITMEX) {
            final BitmexService bitmexService = (BitmexService) left;
            while (bitmexService.getMarketState() == MarketState.SYSTEM_OVERLOADED) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log.error("Sleep error", e);
                    return true;
                }
                if (bitmexService.isMarketStopped()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void replaceLimitOrdersRight(List<FplayOrder> okexOO, Long tradeId) {
        final PlacingType okexPlacingType = settingsRepositoryService.getSettings().getOkexPlacingType();
        replaceLimitOrders(arbitrageService.getRightMarketService(), okexPlacingType, okexOO, tradeId, null);
    }

    private void replaceLimitOrders(MarketServicePreliq marketService, PlacingType placingType, List<FplayOrder> currOrders, Long tradeId,
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
                final String warnStr = String.format("#null WARNING counter is null!!!. orderToCancel=%s, stub=%s", lastOO, stub);
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

                if (marketService.getArbType() == ArbType.RIGHT
                        && marketService.getMarketStaticData() == MarketStaticData.OKEX
                        && amountLeft.signum() <= 0 && marketService.hasDeferredOrders()
                ) {
                    final String warnMsg = String.format("#%s current-volatile. amountLeft=%s and hasDeferredOrders", counterForLogs, amountLeft);
                    fplayTradeService.info(tradeId, counterForLogs, warnMsg);
                    marketService.getTradeLogger().warn(warnMsg);
                    log.info(warnMsg);

                    arbitrageService.getRightMarketService().setMarketState(MarketState.WAITING_ARB);
                } else {
                    final Settings s = settingsRepositoryService.getSettings();
                    signalService.placeOrderOnSignal(marketService, orderType, amountLeft, bestQuotes, placingType, counterName, tradeId,
                            false, portionsQty, s.getArbScheme(), new PlBefore(),
                            btmFokAutoArgs);
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

    private void cancelLimitOrdersLeft(List<FplayOrder> bitmexOO, Long tradeId, PlaceOrderArgs currOkexDeferredArgs) {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        if (bitmexSleepWhileSo(left)) return;

        final FplayOrder lastOO = left.getLastOO(bitmexOO);
        final FplayOrder stub = left.getCurrStub(tradeId, lastOO, bitmexOO);
        final String counterForLogs = stub.getCounterWithPortion(); // no portions here
        if (counterForLogs == null) {
            final String warnStr = String.format("#%s WARNING counter is null!!!. orderToCancel=%s, stub=%s", counterForLogs, lastOO, stub);
            fplayTradeService.info(tradeId, null, warnStr);
            left.getTradeLogger().warn(warnStr);
            log.info(warnStr);
        }

        left.cancelAllOrders(stub, "VolatileMode activated: CancelAllOpenOrders", false, false);
        // freeOoChecker should set READY state after

        final BigDecimal leftFilled = left.getLeftFilledAndUpdateBPriceFact(currOkexDeferredArgs, false);
        final FactPrice bPriceFact = dealPricesRepositoryService.getFullDealPrices(currOkexDeferredArgs.getTradeId()).getBPriceFact();
        if (leftFilled.compareTo(bPriceFact.getFullAmount()) < 0) { // not fully filled
            if (leftFilled.signum() == 0) {
                // unstarted
                final boolean cntUpdated = incAbortedOrUnstartedCounters(currOkexDeferredArgs, false);
                printSignalAborted(currOkexDeferredArgs, "unstarted", leftFilled, cntUpdated);

            } else {
                // aborted
                final boolean cntUpdated = incAbortedOrUnstartedCounters(currOkexDeferredArgs, true);
                printSignalAborted(currOkexDeferredArgs, "aborted", leftFilled, cntUpdated);
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
        arbitrageService.getLeftMarketService().getTradeLogger().info(msg);
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
