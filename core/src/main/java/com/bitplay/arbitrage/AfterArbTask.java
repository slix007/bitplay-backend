package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.AvgPrice;
import com.bitplay.arbitrage.dto.DealPrices;
import com.bitplay.arbitrage.dto.DeltaLogWriter;
import com.bitplay.arbitrage.dto.DiffFactBr;
import com.bitplay.arbitrage.dto.RoundIsNotDoneException;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.GuiLiqParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BorderParams.Ver;
import com.bitplay.persistance.domain.borders.BordersV2;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.fluent.TradeStatus;
import com.bitplay.persistance.domain.settings.Settings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.account.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

@Slf4j
@Getter
@AllArgsConstructor
public class AfterArbTask implements Runnable {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private static final String NA = "NA";

    private final DealPrices dealPrices;
    private final SignalType signalType;
    private final GuiLiqParams guiLiqParams;
    private final DeltaName deltaName;
    private final Long tradeId;
    private final String counterName;
    private final Settings settings;
    private final Position okPosition;
    private final BitmexService bitmexService;
    private final OkCoinService okCoinService;
    private final PreliqUtilsService preliqUtilsService;
    private final PersistenceService persistenceService;
    private final ArbitrageService arbitrageService;
    private final DeltaLogWriter deltaLogWriter;
    private final SlackNotifications slackNotifications;

    @Override
    public void run() {

        try {
            final CumParams cumParams = persistenceService.fetchCumParams();
//            final BigDecimal cm = bitmexService.getCm();

            BigDecimal b_price_fact = fetchBtmFactPrice();
            BigDecimal ok_price_fact = fetchOkFactPrice();

            validateAvgPrice(dealPrices.getbPriceFact());
            validateAvgPrice(dealPrices.getoPriceFact());

            calcCumAndPrintLogs(b_price_fact, ok_price_fact, cumParams);

            arbitrageService.printSumBal(tradeId, counterName); // TODO calcFull balance ???

            persistenceService.saveCumParams(cumParams);

            handleZeroOrders();

            deltaLogWriter.info(String.format("#%s Round completed", counterName));
            deltaLogWriter.setEndStatus(TradeStatus.COMPLETED);

        } catch (RoundIsNotDoneException e) {
            deltaLogWriter.info(String.format("Round is not done: RoundIsNotDoneException: %s. %s. %s", e.getMessage(),
                    arbitrageService.getMainSetStr(),
                    arbitrageService.getExtraSetStr()));
            deltaLogWriter.setEndStatus(TradeStatus.INTERRUPTED);
            final String msg = String.format("%s::#%s Round is not done: RoundIsNotDoneException: %s", tradeId, counterName, e.getMessage());
            log.error(msg, e);
            slackNotifications.sendNotify(NotifyType.ROUND_IS_NOT_DONE, String.format("%s Round is not done", counterName));
        } catch (Exception e) {
            deltaLogWriter.info(String.format("Round is not done: Exception:: %s. %s. %s", e.getMessage(),
                    arbitrageService.getMainSetStr(),
                    arbitrageService.getExtraSetStr()));
            deltaLogWriter.setEndStatus(TradeStatus.INTERRUPTED);
            final String msg = String.format("%s::#%s Round is not done: Exception: %s", tradeId, counterName, e.getMessage());
            log.error(msg, e);
            slackNotifications.sendNotify(NotifyType.ROUND_IS_NOT_DONE, String.format("%s Round is not done", counterName));
        }
    }

    private void handleZeroOrders() {
        boolean hasZero = false;
        if (dealPrices.getbPriceFact().isZeroOrder()) {
            deltaLogWriter.info("Bitmex plan order amount is 0.");
            deltaLogWriter.setBitmexStatus(TradeMStatus.NONE);
            hasZero = true;
        }
        if (dealPrices.getoPriceFact().isZeroOrder()) {
            deltaLogWriter.info("Okex plan order amount is 0.");
            deltaLogWriter.setOkexStatus(TradeMStatus.NONE);
            hasZero = true;
        }
        if (hasZero) {
            deltaLogWriter.info("Round has zero orders.");
            log.error("Round has zero orders.");
        }
    }

    private void calcCumAndPrintLogs(BigDecimal b_price_fact, BigDecimal ok_price_fact, CumParams cumParams) {

        final BigDecimal con = dealPrices.getbBlock();
        final BigDecimal b_bid = dealPrices.getBestQuotes().getBid1_p();
        final BigDecimal b_ask = dealPrices.getBestQuotes().getAsk1_p();
        final BigDecimal ok_bid = dealPrices.getBestQuotes().getBid1_o();
        final BigDecimal ok_ask = dealPrices.getBestQuotes().getAsk1_o();

        deltaLogWriter.info(String.format("#%s Params for calc: con=%s, b_bid=%s, b_ask=%s, ok_bid=%s, ok_ask=%s, b_price_fact=%s, ok_price_fact=%s",
                counterName, con, b_bid, b_ask, ok_bid, ok_ask, b_price_fact, ok_price_fact));

        if (deltaName == DeltaName.B_DELTA) {
            cumParams.setCompletedCounter1(cumParams.getCompletedCounter1() + 1);

            cumParams.setCumDelta(cumParams.getCumDelta().add(dealPrices.getDelta1Plan()));

            // b_block = ok_block*100 = con (не идет в логи и на UI)
            // ast_delta1 = -(con / b_bid - con / ok_ask)
            // cum_ast_delta = sum(ast_delta)
            final BigDecimal ast_delta1 = ((con.divide(b_bid, 16, RoundingMode.HALF_UP)).subtract(con.divide(ok_ask, 16, RoundingMode.HALF_UP)))
                    .negate().setScale(8, RoundingMode.HALF_UP);
            cumParams.setAstDelta1(ast_delta1);
            cumParams.setCumAstDelta1((cumParams.getCumAstDelta1().add(cumParams.getAstDelta1())).setScale(8, BigDecimal.ROUND_HALF_UP));
            // ast_delta1_fact = -(con / b_price_fact - con / ok_price_fact)
            // cum_ast_delta_fact = sum(ast_delta_fact)
            final BigDecimal ast_delta1_fact = ((con.divide(b_price_fact, 16, RoundingMode.HALF_UP))
                    .subtract(con.divide(ok_price_fact, 16, RoundingMode.HALF_UP)))
                    .negate().setScale(8, RoundingMode.HALF_UP);
            cumParams.setAstDeltaFact1(ast_delta1_fact);
            cumParams.setCumAstDeltaFact1((cumParams.getCumAstDeltaFact1().add(cumParams.getAstDeltaFact1())).setScale(8, BigDecimal.ROUND_HALF_UP));

            printCumDelta(cumParams.getCumDelta());
            printCom(cumParams, dealPrices);
            printAstDeltaLogs(ast_delta1, cumParams.getCumAstDelta1(), ast_delta1_fact, cumParams.getCumAstDeltaFact1());
            printP2CumBitmexMCom(cumParams);

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

            printP3DeltaFact(cumParams, dealPrices.getDelta1Fact(), deltaFactStr, ast_diff_fact1, ast_diff_fact2,
                    cumParams.getAstDelta1(), cumParams.getAstDeltaFact1(), dealPrices.getDelta1Plan());

            printOAvgPrice();

        } else if (deltaName == DeltaName.O_DELTA) {
            cumParams.setCompletedCounter2(cumParams.getCompletedCounter2() + 1);

            cumParams.setCumDelta(cumParams.getCumDelta().add(dealPrices.getDelta2Plan()));

            // ast_delta2 = -(con / ok_bid - con / b_ask)
            final BigDecimal ast_delta2 = ((con.divide(ok_bid, 16, RoundingMode.HALF_UP)).subtract(con.divide(b_ask, 16, RoundingMode.HALF_UP)))
                    .negate().setScale(8, RoundingMode.HALF_UP);
            cumParams.setAstDelta2(ast_delta2);
            cumParams.setCumAstDelta2((cumParams.getCumAstDelta2().add(cumParams.getAstDelta2())).setScale(8, BigDecimal.ROUND_HALF_UP));
            // ast_delta2_fact = -(con / ok_price_fact - con / b_price_fact)
            // cum_ast_delta_fact = sum(ast_delta_fact)
            final BigDecimal ast_delta2_fact = ((con.divide(ok_price_fact, 16, RoundingMode.HALF_UP))
                    .subtract(con.divide(b_price_fact, 16, RoundingMode.HALF_UP)))
                    .negate().setScale(8, RoundingMode.HALF_UP);
            cumParams.setAstDeltaFact2(ast_delta2_fact);
            cumParams.setCumAstDeltaFact2((cumParams.getCumAstDeltaFact2().add(cumParams.getAstDeltaFact2())).setScale(8, BigDecimal.ROUND_HALF_UP));

            printCumDelta(cumParams.getCumDelta());
            printCom(cumParams, dealPrices);
            printAstDeltaLogs(ast_delta2, cumParams.getCumAstDelta2(), ast_delta2_fact, cumParams.getCumAstDeltaFact2());
            printP2CumBitmexMCom(cumParams);

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
            printP3DeltaFact(cumParams, dealPrices.getDelta2Fact(), deltaFactStr, ast_diff_fact1, ast_diff_fact2,
                    cumParams.getAstDelta2(), cumParams.getAstDeltaFact2(), dealPrices.getDelta2Plan());

            printOAvgPrice();

        }
    }

    private BigDecimal fetchBtmFactPrice() throws RoundIsNotDoneException {
        // workaround. Bitmex sends wrong avgPrice. Fetch detailed history for each order and calc avgPrice.
        final Instant start = Instant.now();

        BigDecimal b_price_fact = BigDecimal.ZERO;
        int attempt = 0;
        int maxAttempts = 5;
        while (attempt < maxAttempts) {
            attempt++;

            try {

                bitmexService.updateAvgPrice(counterName, dealPrices.getbPriceFact());
                StringBuilder logBuilder = new StringBuilder();
                b_price_fact = dealPrices.getbPriceFact().getAvg(true, counterName, logBuilder);
                deltaLogWriter.info(logBuilder.toString());
                log.info(logBuilder.toString());
                deltaLogWriter.info(dealPrices.getbPriceFact().getDeltaLogTmp());
                break;

            } catch (RoundIsNotDoneException e) {
                log.warn(e.getMessage());
                deltaLogWriter.info(e.getMessage());
                if (attempt == maxAttempts) {
                    throw e;
                }
                deltaLogWriter.info("Wait 200mc for avgPrice");

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e1) {
                    log.error("Error on Wait 200mc for avgPrice", e1);
                }

            }
        }

        final Instant end = Instant.now();
        log.info(String.format("#%s workaround: Bitmex updateAvgPrice. Attempt=%s. Time: %s",
                counterName, attempt, Duration.between(start, end).toString()));

        return b_price_fact;
    }

    private BigDecimal fetchOkFactPrice() throws RoundIsNotDoneException {

        BigDecimal ok_price_fact = BigDecimal.ZERO;
        int attempt = 0;
        int maxAttempts = 5;
        while (attempt < maxAttempts) {
            attempt++;

            try {
                StringBuilder logBuilder = new StringBuilder();
                ok_price_fact = dealPrices.getoPriceFact().getAvg(true, counterName, logBuilder);
                deltaLogWriter.info(logBuilder.toString());
                log.info(logBuilder.toString());
                deltaLogWriter.info(dealPrices.getoPriceFact().getDeltaLogTmp());

                okCoinService.writeAvgPriceLog();
                break;

            } catch (RoundIsNotDoneException e) {
                log.warn(e.getMessage());
                deltaLogWriter.info(e.getMessage());
                if (attempt == maxAttempts) {
                    throw e;
                }
                deltaLogWriter.info("Wait 200mc for avgPrice");

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e1) {
                    log.error("Error on Wait 200mc for avgPrice", e1);
                }

                okCoinService.updateAvgPrice(counterName, dealPrices.getoPriceFact());
            }
        }

        return ok_price_fact;
    }


    private void validateAvgPrice(AvgPrice avgPrice) throws RoundIsNotDoneException {
        if (avgPrice.isItemsEmpty()) {
            throw new RoundIsNotDoneException(avgPrice.getMarketName() + " has no orders");
        }
    }

    private void printP3DeltaFact(CumParams cumParams,
            BigDecimal deltaFact, String deltaFactString, BigDecimal ast_diff_fact1, BigDecimal ast_diff_fact2, BigDecimal ast_delta,
            BigDecimal ast_delta_fact, BigDecimal delta) {

        cumParams.setCumDeltaFact(cumParams.getCumDeltaFact().add(deltaFact));

        BigDecimal diff_fact_v1 = dealPrices.getDiffB().val.add(dealPrices.getDiffO().val);

        cumParams.setCumDiffFact1(cumParams.getCumDiffFact1().add(dealPrices.getDiffB().val));
        cumParams.setCumDiffFact2(cumParams.getCumDiffFact2().add(dealPrices.getDiffO().val));

        // diff_fact = delta_fact - delta
        // cum_diff_fact = sum(diff_fact)
        final BigDecimal diff_fact_v2 = deltaFact.subtract(delta);
        final BigDecimal cumDiffFact = cumParams.getCumDiffFact().add(diff_fact_v2);
        cumParams.setCumDiffFact(cumDiffFact);

        // 1. diff_fact_br = delta_fact - b (писать после diff_fact) cum_diff_fact_br = sum(diff_fact_br)
//        final ArbUtils.DiffFactBr diffFactBr = ArbUtils.getDeltaFactBr(deltaFact, Collections.unmodifiableList(dealPrices.getBorderList()));
        DiffFactBr diffFactBr = new DiffFactBr(BigDecimal.valueOf(-99999999), NA);
        if (signalType.isPreliq()) {
            diffFactBr = new DiffFactBr(diff_fact_v2, String.format("diff_fact_v2[%s]", diff_fact_v2));
        } else {
            BorderParams borderParams = dealPrices.getBorderParamsOnStart();
            if (borderParams.getActiveVersion() == Ver.V1) {
                BigDecimal wam_br = dealPrices.getDeltaName().equals(DeltaName.B_DELTA)
                        ? dealPrices.getBorder1()
                        : dealPrices.getBorder2();

                diffFactBr = new DiffFactBr(deltaFact.subtract(wam_br),
                        String.format("v1[%s-%s]", deltaFact.toPlainString(), wam_br.toPlainString()));

            } else if (borderParams.getActiveVersion() == Ver.V2) {
                final PosMode posMode = borderParams.getPosMode();
                Integer pos_bo = dealPrices.getPos_bo();
                Integer pos_ao = dealPrices.getPlan_pos_ao();
                if (pos_bo != null) {
                    BordersV2 bordersV2 = new BordersV2();
                    BeanUtils.copyProperties(borderParams.getBordersV2(), bordersV2);
                    DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(
                            posMode,
                            pos_bo,
                            pos_ao,
                            dealPrices.getDelta1Plan(),
                            dealPrices.getDelta2Plan(),
                            deltaFact,
                            bordersV2,
                            bitmexService.getContractType().isEth(),
                            bitmexService.getCm()
                    );
                    try {
                        diffFactBr = diffFactBrComputer.compute();
                    } catch (Exception e) {
                        warningLogger.warn(e.toString());
                        deltaLogWriter.warn("WARNING: " + e.toString());
                        log.warn("WARNING", e);
                        cumParams.setDiffFactBrFailsCount(cumParams.getDiffFactBrFailsCount() + 1);
                        warningLogger.warn("diff_fact_br_fails_count = " + cumParams.getDiffFactBrFailsCount());
                    }
                }
            } else {
                String msg = "WARNING: borderParams.activeVersion" + borderParams.getActiveVersion();
                warningLogger.warn(msg);
                deltaLogWriter.warn(msg);
            }
        }

        if (diffFactBr.getStr().equals(NA)) {
            String msg = "WARNING: diff_fact_br=NA=-99999999";
            warningLogger.warn(msg);
        }

        cumParams.setCumDiffFactBr((cumParams.getCumDiffFactBr().add(diffFactBr.getVal())).setScale(2, BigDecimal.ROUND_HALF_UP));

        // cum_ast_diff_fact1 = sum(ast_diff_fact1)
        // cum_ast_diff_fact2 = sum(ast_diff_fact2)
        // ast_diff_fact = ast_delta_fact - ast_delta
        // cum_ast_diff_fact = sum(ast_diff_fact)
        cumParams.setCumAstDiffFact1(cumParams.getCumAstDiffFact1().add(ast_diff_fact1));
        cumParams.setCumAstDiffFact2(cumParams.getCumAstDiffFact2().add(ast_diff_fact2));
        BigDecimal ast_diff_fact = ast_delta_fact.subtract(ast_delta);
        cumParams.setCumAstDiffFact(cumParams.getCumAstDiffFact().add(ast_diff_fact));

        // slip_br = (cum_diff_fact_br - cum_com1 - cum_com2) / (completed counts1 + completed counts2) *2
        // slip    = (cum_diff_fact - cum_com1 - cum_com2) / (completed counts1 + completed counts2) *2
        final BigDecimal cumCom = cumParams.getCumCom1().add(cumParams.getCumCom2());
        final BigDecimal slipBr = (cumParams.getCumDiffFactBr().subtract(cumCom))
                .divide(BigDecimal.valueOf(cumParams.getCompletedCounter1() + cumParams.getCompletedCounter2()), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(2));
        cumParams.setSlipBr(slipBr);
        final BigDecimal slip = (cumParams.getCumDiffFact().subtract(cumCom))
                .divide(BigDecimal.valueOf(cumParams.getCompletedCounter1() + cumParams.getCompletedCounter2()), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(2));
        cumParams.setSlip(slip);

        deltaLogWriter.info(String.format("#%s %s; " +
                        "cum_delta_fact=%s; " +
                        "diff_fact_v1=%s+%s=%s; " +
                        "diff_fact_v2=%s-%s=%s; " +
                        "cum_diff_fact=%s+%s=%s; " +
                        "diff_fact_br=%s=%s\n" +
                        "cum_diff_fact_br=%s; " +
                        "ast_diff_fact1=%s, ast_diff_fact2=%s, ast_diff_fact=%s-%s=%s, " +
                        "cum_ast_diff_fact1=%s, cum_ast_diff_fact2=%s, cum_ast_diff_fact=%s, " +
                        "slipBr=%s, slip=%s",
                counterName,
                deltaFactString,
                cumParams.getCumDeltaFact().toPlainString(),
                dealPrices.getDiffB().val.toPlainString(),
                dealPrices.getDiffO().val.toPlainString(),
                diff_fact_v1.toPlainString(),
                deltaFact.toPlainString(), delta.toPlainString(), diff_fact_v2.toPlainString(),
                cumParams.getCumDiffFact1().toPlainString(),
                cumParams.getCumDiffFact2().toPlainString(),
                cumParams.getCumDiffFact().toPlainString(),
                diffFactBr.getStr(),
                diffFactBr.getVal().toPlainString(),
                cumParams.getCumDiffFactBr().toPlainString(),
                ast_diff_fact1.toPlainString(), ast_diff_fact2.toPlainString(), ast_delta_fact.toPlainString(), ast_delta.toPlainString(),
                ast_diff_fact.toPlainString(),
                cumParams.getCumAstDiffFact1().toPlainString(), cumParams.getCumAstDiffFact2().toPlainString(), cumParams.getCumAstDiffFact().toPlainString(),
                slipBr.toPlainString(), slip.toPlainString()
        ));
    }

    private void printOAvgPrice() {
        deltaLogWriter.info(String.format("o_avg_price_long=%s, o_avg_price_short=%s ",
                okPosition.getPriceAvgLong(),
                okPosition.getPriceAvgShort()));
    }

    private void printCumDelta(BigDecimal cumDelta) {
        deltaLogWriter.info(String.format("#%s cum_delta=%s", counterName, cumDelta.toPlainString()));
    }

    private void printAstDeltaLogs(BigDecimal ast_delta, BigDecimal cum_ast_delta, BigDecimal ast_delta_fact, BigDecimal cum_ast_delta_fact) {
        deltaLogWriter.info(String.format("#%s ast_delta=%s, cum_ast_delta=%s, " +
                        "ast_delta_fact=%s, cum_ast_delta_fact=%s",
                counterName,
                ast_delta.toPlainString(), cum_ast_delta.toPlainString(),
                ast_delta_fact.toPlainString(), cum_ast_delta_fact.toPlainString()));
    }

    private void printCom(CumParams cumParams, DealPrices dealPrices) {
        final BigDecimal bFee = settings.getBFee(dealPrices.getBtmPlacingType());
        final BigDecimal oFee = settings.getOFee(dealPrices.getOkexPlacingType());

        final BigDecimal b_price_fact = dealPrices.getbPriceFact().getAvg();
        final BigDecimal ok_price_fact = dealPrices.getoPriceFact().getAvg();
        final BigDecimal com1 = b_price_fact.multiply(bFee).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);
        final BigDecimal com2 = ok_price_fact.multiply(oFee).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);
        cumParams.setCom1(com1);
        cumParams.setCom2(com2);
        final BigDecimal con = dealPrices.getbBlock();
        // ast_com1 = con / b_price_fact * 0.075 / 100
        // ast_com2 = con / ok_price_fact * 0.015 / 100
        // ast_com = ast_com1 + ast_com2
        final BigDecimal ast_com1 = con.divide(b_price_fact, 16, RoundingMode.HALF_UP).multiply(bFee)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        final BigDecimal ast_com2 = con.divide(ok_price_fact, 16, RoundingMode.HALF_UP).multiply(oFee)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        final BigDecimal ast_com = ast_com1.add(ast_com2);
        cumParams.setAstCom1(ast_com1);
        cumParams.setAstCom2(ast_com2);
        cumParams.setAstCom(ast_com);
        cumParams.setCumAstCom1((cumParams.getCumAstCom1().add(cumParams.getAstCom1())).setScale(8, BigDecimal.ROUND_HALF_UP));
        cumParams.setCumAstCom2((cumParams.getCumAstCom2().add(cumParams.getAstCom2())).setScale(8, BigDecimal.ROUND_HALF_UP));
        cumParams.setCumAstCom((cumParams.getCumAstCom().add(cumParams.getAstCom())).setScale(8, BigDecimal.ROUND_HALF_UP));

        BigDecimal com = com1.add(com2);

        cumParams.setCumCom1(cumParams.getCumCom1().add(com1));
        cumParams.setCumCom2(cumParams.getCumCom2().add(com2));
        BigDecimal cumCom = cumParams.getCumCom1().add(cumParams.getCumCom2());

        deltaLogWriter.info(String.format("#%s com=%s+%s=%s; cum_com=%s+%s=%s; " +
                        "ast_com=%s+%s=%s; cum_ast_com=%s",
                counterName,
                com1.toPlainString(),
                com2.toPlainString(),
                com.toPlainString(),
                cumParams.getCumCom1().toPlainString(),
                cumParams.getCumCom2().toPlainString(),
                cumCom.toPlainString(),
                cumParams.getAstCom1().toPlainString(), cumParams.getAstCom2().toPlainString(), cumParams.getAstCom().toPlainString(),
                cumParams.getCumAstCom().toPlainString()
        ));
    }

    private void printP2CumBitmexMCom(CumParams cumParams) {

        final BigDecimal con = dealPrices.getbBlock();
        final BigDecimal b_price_fact = dealPrices.getbPriceFact().getAvg();

        // bitmex_m_com = round(open_price_fact * 0.025 / 100; 4),
        final BigDecimal BITMEX_M_COM_FACTOR = new BigDecimal(0.025);
        BigDecimal bitmexMCom = b_price_fact.multiply(BITMEX_M_COM_FACTOR)
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);

        cumParams.setCumBitmexMCom((cumParams.getCumBitmexMCom().add(bitmexMCom)).setScale(2, BigDecimal.ROUND_HALF_UP));
        // ast_Bitmex_m_com = con / b_price_fact * 0.025 / 100
        // cum_ast_Bitmex_m_com = sum(ast_Bitmex_m_com)
        final BigDecimal ast_Bitmex_m_com = con.divide(b_price_fact, 16, RoundingMode.HALF_UP).multiply(BITMEX_M_COM_FACTOR)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        cumParams.setAstBitmexMCom(ast_Bitmex_m_com);
        cumParams.setCumAstBitmexMCom((cumParams.getCumAstBitmexMCom().add(cumParams.getAstBitmexMCom())).setScale(8, BigDecimal.ROUND_HALF_UP));

        deltaLogWriter.info(String.format("#%s bitmex_m_com=%s; cum_bitmex_m_com=%s; " +
                        "ast_Bitmex_m_com=%s; cum_ast_Bitmex_m_com=%s",
                counterName,
                bitmexMCom.toPlainString(),
                cumParams.getCumBitmexMCom().toPlainString(),
                cumParams.getAstBitmexMCom().toPlainString(),
                cumParams.getCumAstBitmexMCom().toPlainString()
        ));
    }


}
