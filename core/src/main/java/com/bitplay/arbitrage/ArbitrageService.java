package com.bitplay.arbitrage;

import com.bitplay.TwoMarketStarter;
import com.bitplay.arbitrage.dto.AvgPrice;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.DealPrices;
import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.arbitrage.dto.RoundIsNotDoneException;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.events.SignalEvent;
import com.bitplay.arbitrage.events.SignalEventBus;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.Ver;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.Delta;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.utils.Utils;
import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Service
public class ArbitrageService {

    private static final Logger logger = LoggerFactory.getLogger(ArbitrageService.class);
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    private static final String DELTA1 = "delta1";
    private static final String DELTA2 = "delta2";
    private static final Object calcLock = new Object();
    private final BigDecimal OKEX_FACTOR = BigDecimal.valueOf(100);
    private final DealPrices dealPrices = new DealPrices();
    private boolean firstDeltasAfterStart = true;
    @Autowired
    private BordersService bordersService;
    @Autowired
    private PlacingBlocksService placingBlocksService;
    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private DeltaRepositoryService deltaRepositoryService;
    @Autowired
    private SignalService signalService;
    @Autowired
    private PreliqUtilsService preliqUtilsService;
//    private Disposable schdeduleUpdateBorders;
//    private Instant startTimeToUpdateBorders;
//    private volatile int updateBordersCounter;
    //TODO rename them to first and second
    private MarketService firstMarketService;
    private MarketService secondMarketService;
    private PosDiffService posDiffService;
    private BigDecimal delta1 = BigDecimal.ZERO;
    private BigDecimal delta2 = BigDecimal.ZERO;
    private GuiParams params = new GuiParams();
    private Instant previousEmitTime = Instant.now();
    private String sumBalString = "";
    private volatile Boolean isReadyForTheArbitrage = true;
    private Disposable theTimer;
    private Disposable theCheckBusyTimer;
    private volatile SignalType signalType = SignalType.AUTOMATIC;
    private SignalEventBus signalEventBus = new SignalEventBus();
    private volatile DeltaParams deltaParams = new DeltaParams();

    public DealPrices getDealPrices() {
        return dealPrices;
    }

    public void init(TwoMarketStarter twoMarketStarter) {
        loadParamsFromDb();
        this.firstMarketService = twoMarketStarter.getFirstMarketService();
        this.secondMarketService = twoMarketStarter.getSecondMarketService();
        this.posDiffService = twoMarketStarter.getPosDiffService();
//        startArbitrageMonitoring();
        initArbitrageStateListener();
        initSignalEventBus();
    }

    private void initSignalEventBus() {
        signalEventBus.toObserverable()
                .sample(100, TimeUnit.MILLISECONDS)
                .subscribe(signalEvent -> {
                    try {
                        if (signalEvent == SignalEvent.B_ORDERBOOK_CHANGED
                                || signalEvent == SignalEvent.O_ORDERBOOK_CHANGED) {

                            final OrderBook firstOrderBook = firstMarketService.getOrderBook();
                            final OrderBook secondOrderBook = secondMarketService.getOrderBook();

                            final BestQuotes bestQuotes = calcBestQuotesAndDeltas(firstOrderBook, secondOrderBook);
                            params.setLastOBChange(new Date());

                            doComparison(bestQuotes, firstOrderBook, secondOrderBook);

                            // Logging not often then 5 sec
                            if (Duration.between(previousEmitTime, Instant.now()).getSeconds() > 5
                                    && bestQuotes != null
                                    && bestQuotes.getArbitrageEvent() != BestQuotes.ArbitrageEvent.NONE) {

                                previousEmitTime = Instant.now();
                                signalLogger.info(bestQuotes.toString());
                            }
                        }
                    } catch (NotYetInitializedException e) {
                        // do nothing
                    } catch (Exception e) {
                        logger.error("signalEventBus errorOnEvent", e);
                    }
                }, throwable -> {
                    logger.error("signalEventBus errorOnEvent", throwable);
                    initSignalEventBus();
                });
    }

    public SignalEventBus getSignalEventBus() {
        return signalEventBus;
    }

    private void initArbitrageStateListener() {
        gotFreeListener(firstMarketService.getEventBus());
        gotFreeListener(secondMarketService.getEventBus());
    }

    private void gotFreeListener(EventBus eventBus) {
        eventBus.toObserverable()
                .subscribe(btsEvent -> {
                    try {
                        if (btsEvent == BtsEvent.MARKET_GOT_FREE) {
                            if (!firstMarketService.isBusy() && !secondMarketService.isBusy()) {

                                writeLogArbitrageIsDone();

                                preliqUtilsService.preliqCountersOnRoundDone(true, params, signalType,
                                        firstMarketService, secondMarketService);
                            }
                        }
                    } catch (RoundIsNotDoneException e) {
                        deltasLogger.info("Round is not done. Error: " + e.getMessage());
                        logger.error("Round is not done", e);
                        preliqUtilsService.preliqCountersOnRoundDone(false, params, signalType,
                                firstMarketService, secondMarketService);
                    } catch (Exception e) {
                        deltasLogger.info("Round is not done. Write logs error: " + e.getMessage());
                        logger.error("Round is not done. Write logs error", e);
                        preliqUtilsService.preliqCountersOnRoundDone(false, params, signalType,
                                firstMarketService, secondMarketService);
                    }
                }, throwable -> logger.error("On event handling", throwable));
    }

    private void validateAvgPrice(AvgPrice avgPrice) throws RoundIsNotDoneException {
        if (avgPrice.isItemsEmpty()) {
            throw new RoundIsNotDoneException(avgPrice.getMarketName() + " has no orders");
        }
    }

    private void writeLogArbitrageIsDone() throws RoundIsNotDoneException {
        if (signalType == SignalType.AUTOMATIC && params.getLastDelta() != null && dealPrices.getBestQuotes() != null) {
            final BigDecimal con = dealPrices.getbBlock();
            final BigDecimal b_bid = dealPrices.getBestQuotes().getBid1_p();
            final BigDecimal b_ask = dealPrices.getBestQuotes().getAsk1_p();
            final BigDecimal ok_bid = dealPrices.getBestQuotes().getBid1_o();
            final BigDecimal ok_ask = dealPrices.getBestQuotes().getAsk1_o();

            // workaround. Bitmex sends wrong avgPrice. Fetch detailed history for each order and calc avgPrice.
            final Instant start = Instant.now();
            ((BitmexService) getFirstMarketService()).updateAvgPrice(dealPrices.getbPriceFact());
            ((OkCoinService) getSecondMarketService()).writeAvgPriceLog();
            final Instant end = Instant.now();
            logger.info("workaround: Bitmex updateAvgPrice. Time: " + Duration.between(start, end).toString());

            BigDecimal b_price_fact = dealPrices.getbPriceFact().getAvg(true);
            BigDecimal ok_price_fact = dealPrices.getoPriceFact().getAvg(true);
            if (ok_price_fact.signum() == 0) {
                deltasLogger.info("Wait 200mc for avgPrice");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    logger.error("Error on Wait 200mc for avgPrice");
                }

                ((OkCoinService) getSecondMarketService()).updateAvgPrice(dealPrices.getoPriceFact());
                ((OkCoinService) getSecondMarketService()).writeAvgPriceLog();

                b_price_fact = dealPrices.getbPriceFact().getAvg(true);
                ok_price_fact = dealPrices.getoPriceFact().getAvg(true);
            }

            deltasLogger.info(String.format("#%s Params for calc: con=%s, b_bid=%s, b_ask=%s, ok_bid=%s, ok_ask=%s, b_price_fact=%s, ok_price_fact=%s",
                    getCounter(), con, b_bid, b_ask, ok_bid, ok_ask, b_price_fact, ok_price_fact));

            validateAvgPrice(dealPrices.getbPriceFact());
            validateAvgPrice(dealPrices.getoPriceFact());

            if (params.getLastDelta().equals(DELTA1)) {
                params.setCompletedCounter1(params.getCompletedCounter1() + 1);

                params.setCumDelta(params.getCumDelta().add(dealPrices.getDelta1Plan()));

                // b_block = ok_block*100 = con (не идет в логи и на UI)
                // ast_delta1 = -(con / b_bid - con / ok_ask)
                // cum_ast_delta = sum(ast_delta)
                final BigDecimal ast_delta1 = ((con.divide(b_bid, 16, RoundingMode.HALF_UP)).subtract(con.divide(ok_ask, 16, RoundingMode.HALF_UP)))
                        .negate().setScale(8, RoundingMode.HALF_UP);
                params.setAstDelta1(ast_delta1);
                params.setCumAstDelta1((params.getCumAstDelta1().add(params.getAstDelta1())).setScale(8, BigDecimal.ROUND_HALF_UP));
                // ast_delta1_fact = -(con / b_price_fact - con / ok_price_fact)
                // cum_ast_delta_fact = sum(ast_delta_fact)
                final BigDecimal ast_delta1_fact = ((con.divide(b_price_fact, 16, RoundingMode.HALF_UP)).subtract(con.divide(ok_price_fact, 16, RoundingMode.HALF_UP)))
                        .negate().setScale(8, RoundingMode.HALF_UP);
                params.setAstDeltaFact1(ast_delta1_fact);
                params.setCumAstDeltaFact1((params.getCumAstDeltaFact1().add(params.getAstDeltaFact1())).setScale(8, BigDecimal.ROUND_HALF_UP));

                printCumDelta();
                printCom(dealPrices);
                printAstDeltaLogs(ast_delta1, params.getCumAstDelta1(), ast_delta1_fact, params.getCumAstDeltaFact1());
                printP2CumBitmexMCom();

                // this should be after
                final String deltaFactStr = String.format("delta1_fact=%s-%s=%s",
                        b_price_fact.toPlainString(),
                        ok_price_fact.toPlainString(),
                        dealPrices.getDelta1Fact().toPlainString());
                // if ast_delta = ast_delta1
                //   ast_diff_fact1 = con / b_bid - con / b_price_fact
                //   ast_diff_fact2 = con / ok_price_fact - con / ok_ask
                final BigDecimal ast_diff_fact1 = ((con.divide(b_bid, 16, RoundingMode.HALF_UP)).subtract(con.divide(b_price_fact, 16, RoundingMode.HALF_UP)))
                        .setScale(8, RoundingMode.HALF_UP);
                final BigDecimal ast_diff_fact2 = ((con.divide(ok_price_fact, 16, RoundingMode.HALF_UP)).subtract(con.divide(ok_ask, 16, RoundingMode.HALF_UP)))
                        .setScale(8, RoundingMode.HALF_UP);

                printP3DeltaFact(dealPrices.getDelta1Fact(), deltaFactStr, ast_diff_fact1, ast_diff_fact2,
                        params.getAstDelta1(), params.getAstDeltaFact1(), dealPrices.getDelta1Plan());

                printOAvgPrice();

            } else if (params.getLastDelta().equals(DELTA2)) {
                params.setCompletedCounter2(params.getCompletedCounter2() + 1);

                params.setCumDelta(params.getCumDelta().add(dealPrices.getDelta2Plan()));

                // ast_delta2 = -(con / ok_bid - con / b_ask)
                final BigDecimal ast_delta2 = ((con.divide(ok_bid, 16, RoundingMode.HALF_UP)).subtract(con.divide(b_ask, 16, RoundingMode.HALF_UP)))
                        .negate().setScale(8, RoundingMode.HALF_UP);
                params.setAstDelta2(ast_delta2);
                params.setCumAstDelta2((params.getCumAstDelta2().add(params.getAstDelta2())).setScale(8, BigDecimal.ROUND_HALF_UP));
                // ast_delta2_fact = -(con / ok_price_fact - con / b_price_fact)
                // cum_ast_delta_fact = sum(ast_delta_fact)
                final BigDecimal ast_delta2_fact = ((con.divide(ok_price_fact, 16, RoundingMode.HALF_UP)).subtract(con.divide(b_price_fact, 16, RoundingMode.HALF_UP)))
                        .negate().setScale(8, RoundingMode.HALF_UP);
                params.setAstDeltaFact2(ast_delta2_fact);
                params.setCumAstDeltaFact2((params.getCumAstDeltaFact2().add(params.getAstDeltaFact2())).setScale(8, BigDecimal.ROUND_HALF_UP));

                printCumDelta();
                printCom(dealPrices);
                printAstDeltaLogs(ast_delta2, params.getCumAstDelta2(), ast_delta2_fact, params.getCumAstDeltaFact2());
                printP2CumBitmexMCom();

                final String deltaFactStr = String.format("delta2_fact=%s-%s=%s",
                        ok_price_fact.toPlainString(),
                        b_price_fact.toPlainString(),
                        dealPrices.getDelta2Fact().toPlainString());
                // if ast_delta = ast_delta2
                //   ast_diff_fact1 = con / ok_bid - con / ok_price_fact
                //   ast_diff_fact2 = con / b_price_fact - con / b_ask

                //   ast_diff_fact1 = con / b_price_fact - con / b_ask
                //   ast_diff_fact2 = con / ok_bid - con / ok_price_fact
                final BigDecimal ast_diff_fact1 = ((con.divide(b_price_fact, 16, RoundingMode.HALF_UP)).subtract(con.divide(b_ask, 16, RoundingMode.HALF_UP)))
                        .setScale(8, RoundingMode.HALF_UP);
                final BigDecimal ast_diff_fact2 = ((con.divide(ok_bid, 16, RoundingMode.HALF_UP)).subtract(con.divide(ok_price_fact, 16, RoundingMode.HALF_UP)))
                        .setScale(8, RoundingMode.HALF_UP);
                printP3DeltaFact(dealPrices.getDelta2Fact(), deltaFactStr, ast_diff_fact1, ast_diff_fact2,
                        params.getAstDelta2(), params.getAstDeltaFact2(), dealPrices.getDelta2Plan());

                printOAvgPrice();

            }

            printSumBal(false);

            deltasLogger.info("#{} Round completed", getCounter());

            saveParamsToDb();
        }

    }

    private void printP3DeltaFact(BigDecimal deltaFact, String deltaFactString, BigDecimal ast_diff_fact1, BigDecimal ast_diff_fact2, BigDecimal ast_delta, BigDecimal ast_delta_fact, BigDecimal delta) {

        params.setCumDeltaFact(params.getCumDeltaFact().add(deltaFact));

        BigDecimal diff_fact_v1 = dealPrices.getDiffB().val.add(dealPrices.getDiffO().val);

        params.setCumDiffFact1(params.getCumDiffFact1().add(dealPrices.getDiffB().val));
        params.setCumDiffFact2(params.getCumDiffFact2().add(dealPrices.getDiffO().val));

        // diff_fact = delta_fact - delta
        // cum_diff_fact = sum(diff_fact)
        final BigDecimal diff_fact_v2 = deltaFact.subtract(delta);
        final BigDecimal cumDiffFact = params.getCumDiffFact().add(diff_fact_v2);
        params.setCumDiffFact(cumDiffFact);

        // 1. diff_fact_br = delta_fact - b (писать после diff_fact) cum_diff_fact_br = sum(diff_fact_br)
        final ArbUtils.DiffFactBr diffFactBr = ArbUtils.getDeltaFactBr(deltaFact, Collections.unmodifiableList(dealPrices.getBorderList()));
        params.setCumDiffFactBr((params.getCumDiffFactBr().add(diffFactBr.val)).setScale(2, BigDecimal.ROUND_HALF_UP));

        // cum_ast_diff_fact1 = sum(ast_diff_fact1)
        // cum_ast_diff_fact2 = sum(ast_diff_fact2)
        // ast_diff_fact = ast_delta_fact - ast_delta
        // cum_ast_diff_fact = sum(ast_diff_fact)
        params.setCumAstDiffFact1(params.getCumAstDiffFact1().add(ast_diff_fact1));
        params.setCumAstDiffFact2(params.getCumAstDiffFact2().add(ast_diff_fact2));
        BigDecimal ast_diff_fact = ast_delta_fact.subtract(ast_delta);
        params.setCumAstDiffFact(params.getCumAstDiffFact().add(ast_diff_fact));

        // slip = (cum_diff_fact - cum_com) / count1 + count2
        final BigDecimal cumCom = params.getCumCom1().add(params.getCumCom2());
        final BigDecimal slip = (params.getCumDiffFact().subtract(cumCom))
                .divide(BigDecimal.valueOf(params.getCompletedCounter1() + params.getCompletedCounter2()), 8, RoundingMode.HALF_UP);
        params.setSlip(slip);

        deltasLogger.info(String.format("#%s %s; " +
                        "cum_delta_fact=%s; " +
                        "diff_fact_v1=%s+%s=%s; " +
                        "diff_fact_v2=%s-%s=%s; " +
                        "cum_diff_fact=%s+%s=%s; " +
                        "diff_fact_br=%s=%s\n" +
                        "cum_diff_fact_br=%s; " +
                        "ast_diff_fact1=%s, ast_diff_fact2=%s, ast_diff_fact=%s-%s=%s, " +
                        "cum_ast_diff_fact1=%s, cum_ast_diff_fact2=%s, cum_ast_diff_fact=%s, " +
                        "slip=%s",
                getCounter(),
                deltaFactString,
                params.getCumDeltaFact().toPlainString(),
                dealPrices.getDiffB().val.toPlainString(),
                dealPrices.getDiffO().val.toPlainString(),
                diff_fact_v1.toPlainString(),
                deltaFact.toPlainString(), delta.toPlainString(), diff_fact_v2.toPlainString(),
                params.getCumDiffFact1().toPlainString(),
                params.getCumDiffFact2().toPlainString(),
                params.getCumDiffFact().toPlainString(),
                diffFactBr.str, diffFactBr.val.toPlainString(),
                params.getCumDiffFactBr().toPlainString(),
                ast_diff_fact1.toPlainString(), ast_diff_fact2.toPlainString(), ast_delta_fact.toPlainString(), ast_delta.toPlainString(), ast_diff_fact.toPlainString(),
                params.getCumAstDiffFact1().toPlainString(), params.getCumAstDiffFact2().toPlainString(), params.getCumAstDiffFact().toPlainString(),
                slip.toPlainString()
        ));
    }

    private void printOAvgPrice() {
        deltasLogger.info(String.format("o_avg_price_long=%s, o_avg_price_short=%s ",
                getSecondMarketService().getPosition().getPriceAvgLong(),
                getSecondMarketService().getPosition().getPriceAvgShort()));
    }

    public MarketService getFirstMarketService() {
        return firstMarketService;
    }

    public MarketService getSecondMarketService() {
        return secondMarketService;
    }

    private void setTimeoutAfterStartTrading() {
        isReadyForTheArbitrage = false;
        if (theTimer != null) {
            theTimer.dispose();
        }
        theTimer = Completable.timer(100, TimeUnit.MILLISECONDS)
                .doOnComplete(() -> isReadyForTheArbitrage = true)
                .doOnError(throwable -> logger.error("onError timer", throwable))
                .repeat()
                .retry()
                .subscribe();
        setBusyStackChecker();
    }

    private void setBusyStackChecker() {

        if (theCheckBusyTimer != null) {
            theCheckBusyTimer.dispose();
        }

        theCheckBusyTimer = Completable.timer(6, TimeUnit.MINUTES, Schedulers.computation())
                .doOnComplete(() -> {
                    if (firstMarketService.isMarketStopped()
                            || secondMarketService.isMarketStopped()
                            || firstMarketService.getMarketState() == MarketState.SWAP_AWAIT
                            || secondMarketService.getMarketState() == MarketState.SWAP_AWAIT
                            || firstMarketService.getMarketState() == MarketState.SWAP
                            || secondMarketService.getMarketState() == MarketState.SWAP
                            ) {
                        // do nothing

                    } else if (firstMarketService.isBusy() || secondMarketService.isBusy()) {
                        final String logString = String.format("#%s Warning: busy by isBusy for 6 min. first:%s(%s), second:%s(%s)",
                                getCounter(),
                                firstMarketService.isBusy(),
                                firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isBusy(),
                                secondMarketService.getOnlyOpenOrders().size());
                        deltasLogger.warn(logString);
                        warningLogger.warn(logString);


                        if (firstMarketService.isBusy() && !firstMarketService.hasOpenOrders()) {
                            deltasLogger.warn("Warning: Free Bitmex");
                            warningLogger.warn("Warning: Free Bitmex");
                            firstMarketService.getEventBus().send(BtsEvent.MARKET_FREE);
                        }

                        if (secondMarketService.isBusy() && !secondMarketService.hasOpenOrders()) {
                            deltasLogger.warn("Warning: Free Okcoin");
                            warningLogger.warn("Warning: Free Okcoin");
                            secondMarketService.getEventBus().send(BtsEvent.MARKET_FREE);
                        }

                    } else if (!firstMarketService.isReadyForArbitrage() || !secondMarketService.isReadyForArbitrage()) {
                        final String logString = String.format("#%s Warning: busy for 6 min. first:isReady=%s(Orders=%s), second:isReady=%s(Orders=%s)",
                                getCounter(),
                                firstMarketService.isReadyForArbitrage(), firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isReadyForArbitrage(), secondMarketService.getOnlyOpenOrders().size());
                        deltasLogger.warn(logString);
                        warningLogger.warn(logString);
                    }
                })
                .repeat()
                .retry()
                .subscribe();
    }

    private BestQuotes calcBestQuotesAndDeltas(OrderBook bitmexOrderBook, OrderBook okCoinOrderBook) {
        BestQuotes bestQuotes = new BestQuotes(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        if (bitmexOrderBook != null && okCoinOrderBook != null) {

            if (okCoinOrderBook.getAsks().size() == 0 || okCoinOrderBook.getBids().size() == 0
                    || bitmexOrderBook.getAsks().size() == 0 || bitmexOrderBook.getBids().size() == 0) {
                return BestQuotes.empty();
            }

            // 1. Calc deltas
            bestQuotes = Utils.createBestQuotes(okCoinOrderBook, bitmexOrderBook);
            if (!bestQuotes.hasEmpty()) {
                if (firstDeltasAfterStart) {
                    firstDeltasAfterStart = false;
                    warningLogger.info("Started: First delta calculated");
                }

                delta1 = bestQuotes.getBid1_p().subtract(bestQuotes.getAsk1_o());
                delta2 = bestQuotes.getBid1_o().subtract(bestQuotes.getAsk1_p());
                if (delta1.compareTo(deltaParams.getbDeltaMin()) < 0) {
                    deltaParams.setbDeltaMin(delta1);
                }
                if (delta1.compareTo(deltaParams.getbDeltaMax()) > 0) {
                    deltaParams.setbDeltaMax(delta1);
                }
                if (delta2.compareTo(deltaParams.getoDeltaMin()) < 0) {
                    deltaParams.setoDeltaMin(delta2);
                }
                if (delta2.compareTo(deltaParams.getoDeltaMax()) > 0) {
                    deltaParams.setoDeltaMax(delta2);
                }

                if (!Thread.interrupted()) {
                    deltaRepositoryService.add(new Delta(new Date(),
                            bestQuotes.getAsk1_p(), bestQuotes.getBid1_p(),
                            bestQuotes.getAsk1_o(), bestQuotes.getBid1_o()));

                    persistenceService.storeDeltaParams(deltaParams);

                } else {
                    return bestQuotes;
                }
            } else {
                return bestQuotes;
            }
        }

        return bestQuotes;
    }

    private void doComparison(BestQuotes bestQuotes, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook) {

        if (firstMarketService.isMarketStopped() || secondMarketService.isMarketStopped()) {
            // do nothing

        } else if (!isReadyForTheArbitrage) {
            debugLog.info("isReadyForTheArbitrage=false");
        } else {
            if (Thread.holdsLock(calcLock)) {
                logger.warn("calcLock is in progress");
            }
            synchronized (calcLock) {

                if (bitmexOrderBook != null
                        && okCoinOrderBook != null
                        && firstMarketService.getAccountInfoContracts() != null
                        && secondMarketService.getAccountInfoContracts() != null) {
                    calcAndDoArbitrage(bestQuotes, bitmexOrderBook, okCoinOrderBook);
                }
            }
        }

    }

    private BestQuotes calcAndDoArbitrage(BestQuotes bestQuotes, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook) {

        final BigDecimal bP = firstMarketService.getPosition().getPositionLong();
        final BigDecimal oPL = secondMarketService.getPosition().getPositionLong();
        final BigDecimal oPS = secondMarketService.getPosition().getPositionShort();

        final BorderParams borderParams = persistenceService.fetchBorders();
        if (borderParams == null || borderParams.getActiveVersion() == Ver.V1) {
            BigDecimal border1 = params.getBorder1();
            BigDecimal border2 = params.getBorder2();

            if (delta1.compareTo(border1) >= 0) {
                PlBlocks plBlocks = placingBlocksService.getPlacingBlocks(bitmexOrderBook, okCoinOrderBook, border1,
                        PlacingBlocks.DeltaBase.B_DELTA, oPL, oPS);
                if (plBlocks.getBlockOkex().signum() == 0) {
                    return bestQuotes;
                }
                String dynDeltaLogs = null;
                if (plBlocks.isDynamic()) {
                    plBlocks = dynBlockDecriseByAffordable(DELTA1, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex());
                    dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex())
                            + plBlocks.getDebugLog();
                }

                if (plBlocks.getBlockOkex().signum() > 0) {
                    dealPrices.setBorder(border1);
                    startTradingOnDelta1(SignalType.AUTOMATIC, bestQuotes, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(),
                            null, dynDeltaLogs, null);
                }
            }
            if (delta2.compareTo(border2) >= 0) {
                PlBlocks plBlocks = placingBlocksService.getPlacingBlocks(bitmexOrderBook, okCoinOrderBook, border2,
                        PlacingBlocks.DeltaBase.O_DELTA, oPL, oPS);
                if (plBlocks.getBlockOkex().signum() == 0) {
                    return bestQuotes;
                }
                String dynDeltaLogs = null;
                if (plBlocks.isDynamic()) {
                    plBlocks = dynBlockDecriseByAffordable(DELTA2, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex());
                    dynDeltaLogs = composeDynBlockLogs("o_delta", bitmexOrderBook, okCoinOrderBook, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex())
                            + plBlocks.getDebugLog();
                }
                if (plBlocks.getBlockOkex().signum() > 0) {
                    dealPrices.setBorder(border2);
                    startTradingOnDelta2(SignalType.AUTOMATIC, bestQuotes, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(),
                            null, dynDeltaLogs, null);
                }
            }

        } else if (borderParams.getActiveVersion() == Ver.V2) {
            final BordersService.TradingSignal tradingSignal = bordersService.checkBorders(
                    bitmexOrderBook, okCoinOrderBook, delta1, delta2, bP, oPL, oPS);

            if (tradingSignal.okexBlock == 0) {
                return bestQuotes;
            }

            if (tradingSignal.tradeType == BordersService.TradeType.DELTA1_B_SELL_O_BUY) {
                if (tradingSignal.ver == PlacingBlocks.Ver.DYNAMIC) {
                    final PlBlocks bl = dynBlockDecriseByAffordable(DELTA1, BigDecimal.valueOf(tradingSignal.bitmexBlock), BigDecimal.valueOf(tradingSignal.okexBlock));
                    if (bl.getBlockOkex().signum() > 0) {
                        final BordersService.TradingSignal ts = bordersService.setNewBlock(tradingSignal, bl.getBlockOkex().intValueExact());
                        final BigDecimal b_block = BigDecimal.valueOf(ts.bitmexBlock);
                        final BigDecimal o_block = BigDecimal.valueOf(ts.okexBlock);
                        if (b_block.signum() > 0 && o_block.signum() > 0) {
                            final String dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, b_block, o_block)
                                    + bl.getDebugLog();
                            dealPrices.setBorderList(tradingSignal.borderValueList);
                            startTradingOnDelta1(SignalType.AUTOMATIC, bestQuotes, b_block, o_block, tradingSignal, dynDeltaLogs, null);
                        } else {
                            warningLogger.warn("Block calc(after border2Calc): Block should be > 0, but okexBlock=" + bl.getBlockOkex());
                        }
                    }
                } else {
                    final BigDecimal b_block = BigDecimal.valueOf(tradingSignal.bitmexBlock);
                    final BigDecimal o_block = BigDecimal.valueOf(tradingSignal.okexBlock);
                    dealPrices.setBorderList(tradingSignal.borderValueList);
                    startTradingOnDelta1(SignalType.AUTOMATIC, bestQuotes, b_block, o_block, tradingSignal, null, null);
                }
            }

            if (tradingSignal.tradeType == BordersService.TradeType.DELTA2_B_BUY_O_SELL) {
                if (tradingSignal.ver == PlacingBlocks.Ver.DYNAMIC) {
                    final PlBlocks bl = dynBlockDecriseByAffordable(DELTA2, BigDecimal.valueOf(tradingSignal.bitmexBlock), BigDecimal.valueOf(tradingSignal.okexBlock));
                    if (bl.getBlockOkex().signum() > 0) {
                        final BordersService.TradingSignal ts = bordersService.setNewBlock(tradingSignal, bl.getBlockOkex().intValueExact());
                        final BigDecimal b_block = BigDecimal.valueOf(ts.bitmexBlock);
                        final BigDecimal o_block = BigDecimal.valueOf(ts.okexBlock);
                        if (b_block.signum() > 0 && o_block.signum() > 0) {
                            final String dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, b_block, o_block)
                                    + bl.getDebugLog();
                            dealPrices.setBorderList(tradingSignal.borderValueList);
                            startTradingOnDelta2(SignalType.AUTOMATIC, bestQuotes, b_block, o_block, tradingSignal, dynDeltaLogs, null);
                        } else {
                            warningLogger.warn("Block calc(after border2Calc): Block should be > 0, but okexBlock=" + bl.getBlockOkex());
                        }
                    }
                } else {
                    final BigDecimal b_block = BigDecimal.valueOf(tradingSignal.bitmexBlock);
                    final BigDecimal o_block = BigDecimal.valueOf(tradingSignal.okexBlock);
                    dealPrices.setBorderList(tradingSignal.borderValueList);
                    startTradingOnDelta2(SignalType.AUTOMATIC, bestQuotes, b_block, o_block, tradingSignal, null, null);
                }
            }
        }

        return bestQuotes;
    }

    private String composeDynBlockLogs(String deltaName, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook, BigDecimal b_block, BigDecimal o_block) {
        final String bMsg = Utils.getTenAskBid(bitmexOrderBook, signalType.getCounterName(),
                "Bitmex OrderBook");
        final String oMsg = Utils.getTenAskBid(okCoinOrderBook, signalType.getCounterName(),
                "Okex OrderBook");
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();
        return String.format("%s: Dynamic: dynMaxBlockOkex=%s, b_block=%s, o_block=%s\n%s\n%s. ",
                deltaName,
                placingBlocks.getDynMaxBlockOkex(),
                b_block, o_block,
                bMsg, oMsg);
    }

    public void startPreliqOnDelta1(SignalType signalType, BestQuotes bestQuotes) {
        // border V1
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        final BigDecimal b_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockBitmex());
        final BigDecimal o_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockOkex());
        startTradingOnDelta1(signalType, bestQuotes, b_block, o_block, null, null, PlacingType.TAKER);
    }

    private void startTradingOnDelta1(SignalType signalType, final BestQuotes bestQuotes, final BigDecimal b_block, final BigDecimal o_block,
                                      final BordersService.TradingSignal tradingSignal, String dynamicDeltaLogs,
                                      PlacingType predefinedPlacingType) {
        final BigDecimal ask1_o = bestQuotes.getAsk1_o();
        final BigDecimal bid1_p = bestQuotes.getBid1_p();
        if (checkBalanceBorder1(DELTA1, b_block, o_block) //) {
                && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                && posDiffService.isPositionsEqual()
                && !firstMarketService.isMarketStopped() && !secondMarketService.isMarketStopped()
                &&
                (signalType != SignalType.AUTOMATIC ||
                        (firstMarketService.checkLiquidationEdge(Order.OrderType.ASK)
                                && secondMarketService.checkLiquidationEdge(Order.OrderType.BID))
                )) {

            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
            setSignalType(signalType);
            firstMarketService.setBusy();
            secondMarketService.setBusy();
            params.setLastDelta(DELTA1);
            // Market specific params
            params.setPosBefore(new BigDecimal(firstMarketService.getPositionAsString()));
            params.setVolPlan(b_block); // buy

            dealPrices.setoBlock(o_block);
            dealPrices.setbBlock(b_block);
            dealPrices.setDelta1Plan(delta1);
            dealPrices.setDelta2Plan(delta2);
            dealPrices.setbPricePlan(bid1_p);
            dealPrices.setoPricePlan(ask1_o);
            dealPrices.setDeltaName(DeltaName.B_DELTA);
            dealPrices.setBestQuotes(bestQuotes);
            writeLogDelta1(ask1_o, bid1_p, tradingSignal);
            if (dynamicDeltaLogs != null) {
                deltasLogger.info(String.format("#%s %s", getCounter(), dynamicDeltaLogs));
            }

            dealPrices.setbPriceFact(new AvgPrice(b_block, "bitmex"));
            dealPrices.setoPriceFact(new AvgPrice(o_block, "okex"));
            // in scheme MT2 Okex should be the first
            signalService.placeOkexOrderOnSignal(secondMarketService, Order.OrderType.BID, o_block, bestQuotes, signalType, predefinedPlacingType);
            signalService.placeBitmexOrderOnSignal(firstMarketService, Order.OrderType.ASK, b_block, bestQuotes, signalType, predefinedPlacingType);

            setTimeoutAfterStartTrading();

            saveParamsToDb();
        } else {
            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
        }
    }

    public void startPerliqOnDelta2(SignalType signalType, BestQuotes bestQuotes) {
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        final BigDecimal b_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockBitmex());
        final BigDecimal o_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockOkex());
        startTradingOnDelta2(signalType, bestQuotes, b_block, o_block, null, null, PlacingType.TAKER);
    }

    private void startTradingOnDelta2(final SignalType signalType, final BestQuotes bestQuotes, final BigDecimal b_block, final BigDecimal o_block,
                                      final BordersService.TradingSignal tradingSignal, String dynamicDeltaLogs, PlacingType predefinedPlacingType) {
        final BigDecimal ask1_p = bestQuotes.getAsk1_p();
        final BigDecimal bid1_o = bestQuotes.getBid1_o();

        if (checkBalanceBorder1(DELTA2, b_block, o_block) //) {
                && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                && posDiffService.isPositionsEqual()
                &&
                (signalType != SignalType.AUTOMATIC ||
                        (firstMarketService.checkLiquidationEdge(Order.OrderType.BID)
                                && secondMarketService.checkLiquidationEdge(Order.OrderType.ASK))
                )) {

            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
            setSignalType(signalType);
            firstMarketService.setBusy();
            secondMarketService.setBusy();
            params.setLastDelta(DELTA2);
            // Market specific params
            params.setPosBefore(new BigDecimal(firstMarketService.getPositionAsString()));
            params.setVolPlan(b_block.negate());//sell

            dealPrices.setoBlock(o_block);
            dealPrices.setbBlock(b_block);
            dealPrices.setDelta1Plan(delta1);
            dealPrices.setDelta2Plan(delta2);
            dealPrices.setbPricePlan(ask1_p);
            dealPrices.setoPricePlan(bid1_o);
            dealPrices.setDeltaName(DeltaName.O_DELTA);
            dealPrices.setBestQuotes(bestQuotes);
            writeLogDelta2(ask1_p, bid1_o, tradingSignal);
            if (dynamicDeltaLogs != null) {
                deltasLogger.info(String.format("#%s %s", getCounter(), dynamicDeltaLogs));
            }

            dealPrices.setbPriceFact(new AvgPrice(b_block, "bitmex"));
            dealPrices.setoPriceFact(new AvgPrice(o_block, "okex"));
            // in scheme MT2 Okex should be the first
            signalService.placeOkexOrderOnSignal(secondMarketService, Order.OrderType.ASK, o_block, bestQuotes, signalType, predefinedPlacingType);
            signalService.placeBitmexOrderOnSignal(firstMarketService, Order.OrderType.BID, b_block, bestQuotes, signalType, predefinedPlacingType);

            setTimeoutAfterStartTrading();

            saveParamsToDb();

        } else {
            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
        }
    }

    private void writeLogDelta1(BigDecimal ask1_o, BigDecimal bid1_p, final BordersService.TradingSignal tradingSignal) {
        deltasLogger.info("------------------------------------------");

        Integer counter1 = params.getCounter1();
        Integer counter2 = params.getCounter2();
        BigDecimal border1 = params.getBorder1();

        counter1 += 1;
        params.setCounter1(counter1);

        String iterationMarker = "";
        if (counter1.equals(counter2)) {
            iterationMarker = "whole iteration";
        }
        deltasLogger.info(String.format("#%s count=%s+%s=%s %s", counter1 + counter2, counter1, counter2, counter1 + counter2, iterationMarker));

        deltasLogger.info(String.format("#%s delta1=%s-%s=%s; %s",
                //usdP=%s; btcO=%s; usdO=%s; w=%s; ",
                getCounter(),
                bid1_p.toPlainString(), ask1_o.toPlainString(),
                delta1.toPlainString(),
                tradingSignal == null ? ("b1=" + border1.toPlainString()) : ("borderV2:" + tradingSignal.toString())
        ));

        printSumBal(false);
    }

    private void writeLogDelta2(BigDecimal ask1_p, BigDecimal bid1_o, final BordersService.TradingSignal tradingSignal) {
        deltasLogger.info("------------------------------------------");

        Integer counter1 = params.getCounter1();
        Integer counter2 = params.getCounter2();
        BigDecimal border2 = params.getBorder2();

        counter2 += 1;
        params.setCounter2(counter2);

        String iterationMarker = "";
        if (counter1.equals(counter2)) {
            iterationMarker = "whole iteration";
        }
        deltasLogger.info(String.format("#%s count=%s+%s=%s %s", getCounter(), counter1, counter2, counter1 + counter2, iterationMarker));

        deltasLogger.info(String.format("#%s delta2=%s-%s=%s; %s",
                getCounter(),
                bid1_o.toPlainString(), ask1_p.toPlainString(),
                delta2.toPlainString(),
                tradingSignal == null ? ("b2=" + border2.toPlainString()) : ("borderV2:" + tradingSignal.toString())
        ));

        printSumBal(false);
    }

    private void printCumDelta() {
        deltasLogger.info(String.format("#%s cum_delta=%s", getCounter(), params.getCumDelta().toPlainString()));
    }

    private void printAstDeltaLogs(BigDecimal ast_delta, BigDecimal cum_ast_delta, BigDecimal ast_delta_fact, BigDecimal cum_ast_delta_fact) {
        deltasLogger.info(String.format("#%s ast_delta=%s, cum_ast_delta=%s, " +
                        "ast_delta_fact=%s, cum_ast_delta_fact=%s",
                getCounter(),
                ast_delta.toPlainString(), cum_ast_delta.toPlainString(),
                ast_delta_fact.toPlainString(), cum_ast_delta_fact.toPlainString()));
    }

    private void printCom(DealPrices dealPrices) {
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final BigDecimal bFee = settings.getBFee();
        final BigDecimal oFee = settings.getOFee();

        final BigDecimal b_price_fact = dealPrices.getbPriceFact().getAvg();
        final BigDecimal ok_price_fact = dealPrices.getoPriceFact().getAvg();
        final BigDecimal com1 = b_price_fact.multiply(bFee).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);
        final BigDecimal com2 = ok_price_fact.multiply(oFee).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);
        params.setCom1(com1);
        params.setCom2(com2);
        final BigDecimal con = dealPrices.getbBlock();
        // ast_com1 = con / b_price_fact * 0.075 / 100
        // ast_com2 = con / ok_price_fact * 0.015 / 100
        // ast_com = ast_com1 + ast_com2
        final BigDecimal ast_com1 = con.divide(b_price_fact, 16, RoundingMode.HALF_UP).multiply(bFee)
                .divide(OKEX_FACTOR, 8, RoundingMode.HALF_UP);
        final BigDecimal ast_com2 = con.divide(ok_price_fact, 16, RoundingMode.HALF_UP).multiply(oFee)
                .divide(OKEX_FACTOR, 8, RoundingMode.HALF_UP);
        final BigDecimal ast_com = ast_com1.add(ast_com2);
        params.setAstCom1(ast_com1);
        params.setAstCom2(ast_com2);
        params.setAstCom(ast_com);
        params.setCumAstCom1((params.getCumAstCom1().add(params.getAstCom1())).setScale(8, BigDecimal.ROUND_HALF_UP));
        params.setCumAstCom2((params.getCumAstCom2().add(params.getAstCom2())).setScale(8, BigDecimal.ROUND_HALF_UP));
        params.setCumAstCom((params.getCumAstCom().add(params.getAstCom())).setScale(8, BigDecimal.ROUND_HALF_UP));

        BigDecimal com = com1.add(com2);

        params.setCumCom1(params.getCumCom1().add(com1));
        params.setCumCom2(params.getCumCom2().add(com2));
        BigDecimal cumCom = params.getCumCom1().add(params.getCumCom2());

        deltasLogger.info(String.format("#%s com=%s+%s=%s; cum_com=%s+%s=%s; " +
                        "ast_com=%s+%s=%s; cum_ast_com=%s",
                getCounter(),
                com1.toPlainString(),
                com2.toPlainString(),
                com.toPlainString(),
                params.getCumCom1().toPlainString(),
                params.getCumCom2().toPlainString(),
                cumCom.toPlainString(),
                params.getAstCom1().toPlainString(), params.getAstCom2().toPlainString(), params.getAstCom().toPlainString(),
                params.getCumAstCom().toPlainString()
        ));
    }

    private void printP2CumBitmexMCom() {

        final BigDecimal con = dealPrices.getbBlock();
        final BigDecimal b_price_fact = dealPrices.getbPriceFact().getAvg();

        // bitmex_m_com = round(open_price_fact * 0.025 / 100; 4),
        final BigDecimal BITMEX_M_COM_FACTOR = new BigDecimal(0.025);
        BigDecimal bitmexMCom = b_price_fact.multiply(BITMEX_M_COM_FACTOR)
                .divide(OKEX_FACTOR, 2, BigDecimal.ROUND_HALF_UP);

        params.setCumBitmexMCom((params.getCumBitmexMCom().add(bitmexMCom)).setScale(2, BigDecimal.ROUND_HALF_UP));
        // ast_Bitmex_m_com = con / b_price_fact * 0.025 / 100
        // cum_ast_Bitmex_m_com = sum(ast_Bitmex_m_com)
        final BigDecimal ast_Bitmex_m_com = con.divide(b_price_fact, 16, RoundingMode.HALF_UP).multiply(BITMEX_M_COM_FACTOR).divide(OKEX_FACTOR, 8, RoundingMode.HALF_UP);
        params.setAstBitmexMCom(ast_Bitmex_m_com);
        params.setCumAstBitmexMCom((params.getCumAstBitmexMCom().add(params.getAstBitmexMCom())).setScale(8, BigDecimal.ROUND_HALF_UP));

        deltasLogger.info(String.format("#%s bitmex_m_com=%s; cum_bitmex_m_com=%s; " +
                        "ast_Bitmex_m_com=%s; cum_ast_Bitmex_m_com=%s",
                getCounter(),
                bitmexMCom.toPlainString(),
                params.getCumBitmexMCom().toPlainString(),
                params.getAstBitmexMCom().toPlainString(),
                params.getCumAstBitmexMCom().toPlainString()
        ));
    }

    @Scheduled(initialDelay = 10 * 1000, fixedDelay = 1000)
    public void calcSumBalForGui() {
        final AccountInfoContracts firstAccount = firstMarketService.calcFullBalance().getAccountInfoContracts();
        final AccountInfoContracts secondAccount = secondMarketService.calcFullBalance().getAccountInfoContracts();
        if (firstAccount != null && secondAccount != null) {
            final BigDecimal bW = firstAccount.getWallet();
            final BigDecimal bEMark = firstAccount.geteMark() != null ? firstAccount.geteMark() : BigDecimal.ZERO;
            final BigDecimal bEbest = firstAccount.geteBest() != null ? firstAccount.geteBest() : BigDecimal.ZERO;
            final BigDecimal bEAvg = firstAccount.geteAvg() != null ? firstAccount.geteAvg() : BigDecimal.ZERO;
            final BigDecimal bU = firstAccount.getUpl();
            final BigDecimal bM = firstAccount.getMargin();
            final BigDecimal bA = firstAccount.getAvailable();

            final BigDecimal oW = secondAccount.getWallet();
            final BigDecimal oELast = secondAccount.geteLast() != null ? secondAccount.geteLast() : BigDecimal.ZERO;
            final BigDecimal oEbest = secondAccount.geteBest() != null ? secondAccount.geteBest() : BigDecimal.ZERO;
            final BigDecimal oEAvg = secondAccount.geteAvg() != null ? secondAccount.geteAvg() : BigDecimal.ZERO;
            final BigDecimal oM = secondAccount.getMargin();
            final BigDecimal oU = secondAccount.getUpl();
            final BigDecimal oA = secondAccount.getAvailable();

            if (bW == null || oW == null) {
                throw new IllegalStateException(String.format("Balance is not yet defined. bW=%s, oW=%s", bW, oW));
            }
            final BigDecimal sumW = bW.add(oW).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumE = bEMark.add(oELast).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumEBest = bEbest.add(oEbest).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumEAvg = bEAvg.add(oEAvg).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumUpl = bU.add(oU).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumM = bM.add(oM).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumA = bA.add(oA).setScale(8, BigDecimal.ROUND_HALF_UP);

            final BigDecimal quAvg = Utils.calcQuAvg(firstMarketService.getOrderBook(), secondMarketService.getOrderBook());

            sumBalString = String.format("s_bal=w%s_%s, s_e_%s_%s, s_e_best%s_%s, s_e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s",
                    sumW.toPlainString(), sumW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumE.toPlainString(), sumE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumEBest.toPlainString(), sumEBest.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumEAvg.toPlainString(), sumEAvg.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumUpl.toPlainString(), sumUpl.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumM.toPlainString(), sumM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumA.toPlainString(), sumA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
    }

    public void printSumBal(boolean isGuiButton) {
        try {
            String counterName = String.valueOf(getCounter());
            if (isGuiButton) {
                counterName = "button";
            } else if (signalType != SignalType.AUTOMATIC) {
                counterName = signalType.getCounterName();
            }

            final AccountInfoContracts firstAccount = firstMarketService.calcFullBalance().getAccountInfoContracts();
            final AccountInfoContracts secondAccount = secondMarketService.calcFullBalance().getAccountInfoContracts();
            if (firstAccount != null && secondAccount != null) {
                final BigDecimal bW = firstAccount.getWallet();
                final BigDecimal bEmark = firstAccount.geteMark() != null ? firstAccount.geteMark() : BigDecimal.ZERO;
                final BigDecimal bEbest = firstAccount.geteBest() != null ? firstAccount.geteBest() : BigDecimal.ZERO;
                final BigDecimal bEavg = firstAccount.geteAvg() != null ? firstAccount.geteAvg() : BigDecimal.ZERO;
                final BigDecimal bU = firstAccount.getUpl();
                final BigDecimal bM = firstAccount.getMargin();
                final BigDecimal bA = firstAccount.getAvailable();
                final BigDecimal bP = firstMarketService.getPosition().getPositionLong();
                final BigDecimal bLv = firstMarketService.getPosition().getLeverage();
                final BigDecimal bAL = firstMarketService.getAffordable().getForLong();
                final BigDecimal bAS = firstMarketService.getAffordable().getForShort();
                final BigDecimal quAvg = Utils.calcQuAvg(firstMarketService.getOrderBook(), secondMarketService.getOrderBook());
                final OrderBook bOrderBook = firstMarketService.getOrderBook();
                final BigDecimal bBestAsk = Utils.getBestAsks(bOrderBook, 1).get(0).getLimitPrice();
                final BigDecimal bBestBid = Utils.getBestBids(bOrderBook, 1).get(0).getLimitPrice();
                deltasLogger.info(String.format("#%s b_bal=w%s_%s, e_mark%s_%s, e_best%s_%s, e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s, p%s, lv%s, lg%s, st%s, ask[1]%s, bid[1]%s",
                        counterName,
                        bW.toPlainString(), bW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bEmark.toPlainString(), bEmark.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bEbest.toPlainString(), bEbest.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bEavg.toPlainString(), bEavg.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bU.toPlainString(), bU.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bM.toPlainString(), bM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bA.toPlainString(), bA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        Utils.withSign(bP),
                        bLv.toPlainString(),
                        Utils.withSign(bAL),
                        Utils.withSign(bAS),
                        bBestAsk,
                        bBestBid
                ));

                final BigDecimal oW = secondAccount.getWallet();
                final BigDecimal oElast = secondAccount.geteLast() != null ? secondAccount.geteLast() : BigDecimal.ZERO;
                final BigDecimal oEbest = secondAccount.geteBest() != null ? secondAccount.geteBest() : BigDecimal.ZERO;
                final BigDecimal oEavg = secondAccount.geteAvg() != null ? secondAccount.geteAvg() : BigDecimal.ZERO;
                final BigDecimal oM = secondAccount.getMargin();
                final BigDecimal oU = secondAccount.getUpl();
                final BigDecimal oA = secondAccount.getAvailable();
                final BigDecimal oPL = secondMarketService.getPosition().getPositionLong();
                final BigDecimal oPS = secondMarketService.getPosition().getPositionShort();
                final BigDecimal oLv = secondMarketService.getPosition().getLeverage();
                final BigDecimal oAL = secondMarketService.getAffordable().getForLong();
                final BigDecimal oAS = secondMarketService.getAffordable().getForShort();
                final OrderBook oOrderBook = secondMarketService.getOrderBook();
                final BigDecimal oBestAsk = Utils.getBestAsks(oOrderBook, 1).get(0).getLimitPrice();
                final BigDecimal oBestBid = Utils.getBestBids(oOrderBook, 1).get(0).getLimitPrice();
                deltasLogger.info(String.format("#%s o_bal=w%s_%s, e_mark%s_%s, e_best%s_%s, e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s, p+%s-%s, lv%s, lg%s, st%s, ask[1]%s, bid[1]%s",
                        counterName,
                        oW.toPlainString(), oW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oElast.toPlainString(), oElast.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oEbest.toPlainString(), oEbest.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oEavg.toPlainString(), oEavg.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oU.toPlainString(), oU.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oM.toPlainString(), oM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oA.toPlainString(), oA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oPL, oPS,
                        oLv.toPlainString(),
                        Utils.withSign(oAL),
                        Utils.withSign(oAS),
                        oBestAsk,
                        oBestBid
                ));

                final BigDecimal sumW = bW.add(oW).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumE = bEmark.add(oElast).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumEbest = bEbest.add(oEbest).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumEavg = bEavg.add(oEavg).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumUpl = bU.add(oU).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumM = bM.add(oM).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumA = bA.add(oA).setScale(8, BigDecimal.ROUND_HALF_UP);

                final String sBalStr = String.format("#%s s_bal=w%s_%s, s_e%s_%s, s_e_best%s_%s, s_e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s",
                        counterName,
                        sumW.toPlainString(), sumW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumE.toPlainString(), sumE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumEbest.toPlainString(), sumEbest.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumEavg.toPlainString(), sumEavg.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumUpl.toPlainString(), sumUpl.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumM.toPlainString(), sumM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumA.toPlainString(), sumA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP));
                deltasLogger.info(sBalStr);

                final String bDQLMin;
                final String oDQLMin;
                if (signalType == SignalType.B_PRE_LIQ || signalType == SignalType.O_PRE_LIQ) {
                    bDQLMin = String.format("b_DQL_close_min=%s", getParams().getbDQLCloseMin());
                    oDQLMin = String.format("o_DQL_close_min=%s", getParams().getoDQLCloseMin());
                } else {
                    bDQLMin = String.format("b_DQL_open_min=%s", getParams().getbDQLOpenMin());
                    oDQLMin = String.format("o_DQL_open_min=%s", getParams().getoDQLOpenMin());
                }

                deltasLogger.info(String.format("#%s Pos diff: %s", counterName, getPosDiffString()));
                final LiqInfo bLiqInfo = getFirstMarketService().getLiqInfo();
                deltasLogger.info(String.format("#%s %s; %s; %s", counterName, bLiqInfo.getDqlString(), bLiqInfo.getDmrlString(), bDQLMin));
                final LiqInfo oLiqInfo = getSecondMarketService().getLiqInfo();
                deltasLogger.info(String.format("#%s %s; %s; %s", counterName, oLiqInfo.getDqlString(), oLiqInfo.getDmrlString(), oDQLMin));
            }
        } catch (Exception e) {
            deltasLogger.info("Error on printSumBal");
            logger.error("Error on printSumBal", e);
        }
    }

    public String getPosDiffString() {
        final BigDecimal posDiff = posDiffService.getPositionsDiffSafe();
        final BigDecimal bP = getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal oPL = getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = getSecondMarketService().getPosition().getPositionShort();
        final BigDecimal ha = getParams().getHedgeAmount();
        final BigDecimal dc = posDiffService.getPositionsDiffWithHedge();
        final BigDecimal mdc = getParams().getMaxDiffCorr();

        return String.format("b(%s) o(+%s-%s) = %s, ha=%s, dc=%s, mdc=%s",
                Utils.withSign(bP),
                oPL.toPlainString(),
                oPS.toPlainString(),
                posDiff.toPlainString(),
                ha, dc, mdc
        );
    }

    private PlBlocks dynBlockDecriseByAffordable(String deltaRef, BigDecimal blockSize1, BigDecimal blockSize2) {
        BigDecimal b1 = BigDecimal.ZERO;
        BigDecimal b2 = BigDecimal.ZERO;
        final Affordable firstAffordable = firstMarketService.recalcAffordable();
        final Affordable secondAffordable = secondMarketService.recalcAffordable();
        if (deltaRef.equals(DELTA1)) {
            // b_sell, o_buy
            final BigDecimal b_sell_lim = firstAffordable.getForShort().signum() < 0 ? BigDecimal.ZERO : firstAffordable.getForShort();
            final BigDecimal o_buy_lim = secondAffordable.getForLong().signum() < 0 ? BigDecimal.ZERO : secondAffordable.getForLong();
            b1 = blockSize1.compareTo(b_sell_lim) < 0 ? blockSize1 : b_sell_lim;
            b2 = blockSize2.compareTo(o_buy_lim) < 0 ? blockSize2 : o_buy_lim;
        } else if (deltaRef.equals(DELTA2)) {
            // buy p , sell o
            final BigDecimal b_buy_lim = firstAffordable.getForLong().signum() < 0 ? BigDecimal.ZERO : firstAffordable.getForLong();
            final BigDecimal o_sell_lim = secondAffordable.getForShort().signum() < 0 ? BigDecimal.ZERO : secondAffordable.getForShort();
            b1 = blockSize1.compareTo(b_buy_lim) < 0 ? blockSize1 : b_buy_lim;
            b2 = blockSize2.compareTo(o_sell_lim) < 0 ? blockSize2 : o_sell_lim;
        }

        if (b1.signum() == 0 || b2.signum() == 0) {
            b1 = BigDecimal.ZERO;
            b2 = BigDecimal.ZERO;
        } else if (b1.compareTo(b2.multiply(OKEX_FACTOR)) != 0) {
            b2 = b2.min(b1.divide(OKEX_FACTOR, 0, RoundingMode.HALF_UP));
            b1 = b2.multiply(OKEX_FACTOR);
        }

        String debugLog = String.format("%s dynBlockDecriseByAffordable: %s, %s. bitmex %s, okex %s",
                getCounter(), b1, b2, firstAffordable, secondAffordable);

        return new PlBlocks(b1, b2, PlacingBlocks.Ver.DYNAMIC, debugLog);
    }

    private boolean checkBalanceBorder1(String deltaRef, BigDecimal blockSize1, BigDecimal blockSize2) {
        boolean affordable = false;
        if (deltaRef.equals(DELTA1)) {
            // sell p, buy o
            if (firstMarketService.isAffordable(Order.OrderType.ASK, blockSize1)
                    && secondMarketService.isAffordable(Order.OrderType.BID, blockSize2)) {
                affordable = true;
            }
        } else if (deltaRef.equals(DELTA2)) {
            // buy p , sell o
            if (firstMarketService.isAffordable(Order.OrderType.BID, blockSize1)
                    && secondMarketService.isAffordable(Order.OrderType.ASK, blockSize2)) {
                affordable = true;
            }
        }

        return affordable;
    }

    public BigDecimal calcQuAvg() {
        return Utils.calcQuAvg(firstMarketService.getOrderBook(), secondMarketService.getOrderBook());
    }

    public BigDecimal getDelta1() {
        return delta1;
    }

    public BigDecimal getDelta2() {
        return delta2;
    }

    public void loadParamsFromDb() {
        final GuiParams deltas = persistenceService.fetchGuiParams();
        if (deltas != null) {
            params = deltas;
        } else {
            params = new GuiParams();
        }

        final DeltaParams deltaParams = persistenceService.fetchDeltaParams();
        if (deltaParams != null) {
            this.deltaParams = deltaParams;
        } else {
            this.deltaParams = new DeltaParams();
        }
    }

    public void saveParamsToDb() {
        persistenceService.saveGuiParams(params);
    }

    public void resetDeltaParams() {
        deltaParams.setbDeltaMin(delta1);
        deltaParams.setbDeltaMax(delta1);
        deltaParams.setoDeltaMin(delta2);
        deltaParams.setoDeltaMax(delta2);
    }

    public GuiParams getParams() {
        return params;
    }

    public void setParams(GuiParams params) {
        this.params = params;
    }

    public DeltaParams getDeltaParams() {
        return deltaParams;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public synchronized void setSignalType(SignalType signalType) {
        this.signalType = signalType != null ? signalType : SignalType.AUTOMATIC;
    }

    public int getCounter() {
        return params.getCounter1() + params.getCounter2();
    }

    public String getSumBalString() {
        return sumBalString;
    }
}
