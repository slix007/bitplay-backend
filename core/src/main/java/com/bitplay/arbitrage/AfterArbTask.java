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
import com.bitplay.model.Pos;
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
import com.bitplay.persistance.domain.settings.TradingMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
    private final Long tradeId;
    private final String counterName;
    private final Settings settings;
    private final Pos okPosition;
    private final BitmexService bitmexService;
    private final OkCoinService okCoinService;
    private final CumService cumService;
    private final ArbitrageService arbitrageService;
    private final DeltaLogWriter deltaLogWriter;
    private final SlackNotifications slackNotifications;

    @Override
    public void run() {

        try {
//            final BigDecimal cm = bitmexService.getCm();

            BigDecimal b_price_fact = fetchBtmFactPrice();
            BigDecimal ok_price_fact = fetchOkFactPrice();

            validateAvgPrice(dealPrices.getbPriceFact());
            validateAvgPrice(dealPrices.getoPriceFact());

            calcCumAndPrintLogs(b_price_fact, ok_price_fact);

            arbitrageService.printSumBal(tradeId, counterName); // TODO calcFull balance ???

//            cumService.saveCumParams(cumParams);

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

    private void calcCumAndPrintLogs(BigDecimal b_price_fact, BigDecimal ok_price_fact) {

        final BigDecimal con = dealPrices.getbBlock();
        final BigDecimal b_bid = dealPrices.getBestQuotes().getBid1_p();
        final BigDecimal b_ask = dealPrices.getBestQuotes().getAsk1_p();
        final BigDecimal ok_bid = dealPrices.getBestQuotes().getBid1_o();
        final BigDecimal ok_ask = dealPrices.getBestQuotes().getAsk1_o();

        deltaLogWriter.info(String.format("#%s Params for calc: con=%s, b_bid=%s, b_ask=%s, ok_bid=%s, ok_ask=%s, b_price_fact=%s, ok_price_fact=%s",
                counterName, con, b_bid, b_ask, ok_bid, ok_ask, b_price_fact, ok_price_fact));

        final TradingMode mode = dealPrices.getTradingMode();
        if (dealPrices.getDeltaName() == DeltaName.B_DELTA) {
            cumService.incCompletedCounter1(mode);

            cumService.addCumDelta(mode, dealPrices.getDelta1Plan());

            // b_block = ok_block*100 = con (не идет в логи и на UI)
            // ast_delta1 = -(con / b_bid - con / ok_ask)
            // cum_ast_delta = sum(ast_delta)
            final BigDecimal ast_delta1 = ((con.divide(b_bid, 16, RoundingMode.HALF_UP)).subtract(con.divide(ok_ask, 16, RoundingMode.HALF_UP)))
                    .negate().setScale(8, RoundingMode.HALF_UP);
            cumService.addAstDelta1(mode, ast_delta1);
//            cumParams.setCumAstDelta1((cumParams.getCumAstDelta1().add(cumParams.getAstDelta1())).setScale(8, BigDecimal.ROUND_HALF_UP));
            // ast_delta1_fact = -(con / b_price_fact - con / ok_price_fact)
            // cum_ast_delta_fact = sum(ast_delta_fact)
            final BigDecimal ast_delta1_fact = ((con.divide(b_price_fact, 16, RoundingMode.HALF_UP))
                    .subtract(con.divide(ok_price_fact, 16, RoundingMode.HALF_UP)))
                    .negate().setScale(8, RoundingMode.HALF_UP);
            cumService.addAstDeltaFact1(mode, ast_delta1_fact);

            printCumDelta(cumService.getTotalCommon().getCumDelta());
            setAndPrintCom(dealPrices);
            printAstDeltaLogs(ast_delta1, cumService.getTotalCommon().getCumAstDelta1(), ast_delta1_fact, cumService.getTotalCommon().getCumAstDeltaFact1());
            setAndPrintP2CumBitmexMCom();

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

            setAndPrintDiff2ConBo(DeltaName.B_DELTA);
            setAndPrintP3DeltaFact(dealPrices.getDelta1Fact(), deltaFactStr, ast_diff_fact1, ast_diff_fact2,
                    cumService.getTotalCommon().getAstDelta1(), cumService.getTotalCommon().getAstDeltaFact1(), dealPrices.getDelta1Plan());

            printOAvgPrice();

        } else if (dealPrices.getDeltaName() == DeltaName.O_DELTA) {
//            cumParams.setCompletedCounter2(cumParams.getCompletedCounter2() + 1);
            cumService.incCompletedCounter2(mode);

            cumService.addCumDelta(mode, dealPrices.getDelta2Plan());

            // ast_delta2 = -(con / ok_bid - con / b_ask)
            final BigDecimal ast_delta2 = ((con.divide(ok_bid, 16, RoundingMode.HALF_UP)).subtract(con.divide(b_ask, 16, RoundingMode.HALF_UP)))
                    .negate().setScale(8, RoundingMode.HALF_UP);
            cumService.addAstDelta2(mode, ast_delta2);

            // ast_delta2_fact = -(con / ok_price_fact - con / b_price_fact)
            // cum_ast_delta_fact = sum(ast_delta_fact)
            final BigDecimal ast_delta2_fact = ((con.divide(ok_price_fact, 16, RoundingMode.HALF_UP))
                    .subtract(con.divide(b_price_fact, 16, RoundingMode.HALF_UP)))
                    .negate().setScale(8, RoundingMode.HALF_UP);
            cumService.addAstDeltaFact2(mode, ast_delta2_fact);

            printCumDelta(cumService.getTotalCommon().getCumDelta());
            setAndPrintCom(dealPrices);
            printAstDeltaLogs(ast_delta2, cumService.getTotalCommon().getCumAstDelta2(), ast_delta2_fact, cumService.getTotalCommon().getCumAstDeltaFact2());
            setAndPrintP2CumBitmexMCom();

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

            setAndPrintDiff2ConBo(DeltaName.O_DELTA);
            setAndPrintP3DeltaFact(dealPrices.getDelta2Fact(), deltaFactStr, ast_diff_fact1, ast_diff_fact2,
                    cumService.getTotalCommon().getAstDelta2(), cumService.getTotalCommon().getAstDeltaFact2(), dealPrices.getDelta2Plan());

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

                bitmexService.updateAvgPrice(counterName, dealPrices.getbPriceFact(), false);
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

    private void setAndPrintDiff2ConBo(DeltaName deltaName) {
        // В дельта-логах добавить в параметр diff2_con_bo, в формате
        //  diff2_con_bo = diff2_pre + diff2_post, где:
        //     для delta1:
        //      diff2_pre = price_plan - place_order_price;
        //      diff2_post = place_order_price - price_fact;
        //     для delta2:
        //      diff2_pre = place_order_price - price_plan;
        //      diff2_post = price_fact - place_order_price;
        final BigDecimal price_fact = dealPrices.getoPriceFact() != null ? dealPrices.getoPriceFact().getAvg() : BigDecimal.ZERO;
        final BigDecimal diff2_pre;
        final BigDecimal diff2_post;
        if (deltaName == DeltaName.B_DELTA) {
            diff2_pre = dealPrices.getoPricePlan().subtract(dealPrices.getoPricePlanOnStart());
            diff2_post = dealPrices.getoPricePlanOnStart().subtract(price_fact);
        } else {
            diff2_pre = dealPrices.getoPricePlanOnStart().subtract(dealPrices.getoPricePlan());
            diff2_post = price_fact.subtract(dealPrices.getoPricePlanOnStart());
        }
        final BigDecimal diff2_con_bo = diff2_pre.add(diff2_post);
        cumService.addCumDiff2(dealPrices.getTradingMode(), diff2_pre, diff2_post);

        final CumParams totalCommon = cumService.getTotalCommon();
        deltaLogWriter.info(String.format("#%s okex diff2_pre=%s, diff2_post=%s, diff2_con_bo=%s; (plan_price=%s, place_order_price=%s);"
                        + "cum_diff2_pre=%s, cum_diff2_post=%s",
                counterName, diff2_pre, diff2_post, diff2_con_bo, dealPrices.getoPricePlan(), dealPrices.getoPricePlanOnStart(),
                totalCommon.getCumDiff2Pre().toPlainString(),
                totalCommon.getCumDiff2Post().toPlainString()));
    }

    private void setAndPrintP3DeltaFact(
            BigDecimal deltaFact, String deltaFactString, BigDecimal ast_diff_fact1, BigDecimal ast_diff_fact2, BigDecimal ast_delta,
            BigDecimal ast_delta_fact, BigDecimal delta) {

        cumService.addDeltaFact(dealPrices.getTradingMode(), deltaFact);

        final BigDecimal diff_fact_v1_b = dealPrices.getDiffB().val;
        final BigDecimal diff_fact_v1_o = dealPrices.getDiffO().val;
        BigDecimal diff_fact_v1 = diff_fact_v1_b.add(diff_fact_v1_o);

        // diff_fact = delta_fact - delta
        // cum_diff_fact = sum(diff_fact)
        final BigDecimal diff_fact_v2 = deltaFact.subtract(delta);
        cumService.addDiffFact(dealPrices.getTradingMode(), diff_fact_v1_b, diff_fact_v1_o, diff_fact_v2);

        // 1. diff_fact_br = delta_fact - b (писать после diff_fact) cum_diff_fact_br = sum(diff_fact_br)
//        final ArbUtils.DiffFactBr diffFactBr = ArbUtils.getDeltaFactBr(deltaFact, Collections.unmodifiableList(dealPrices.getBorderList()));
        DiffFactBr diffFactBr = new DiffFactBr(BigDecimal.valueOf(-99999999), NA);
        if (signalType.isPreliq()) {
            diffFactBr = new DiffFactBr(diff_fact_v2, String.format("diff_fact_v2[%s]", diff_fact_v2));
        } else {
            BorderParams borderParams = dealPrices.getBorderParamsOnStart();
            if (borderParams.getActiveVersion() == Ver.V1) {
                BigDecimal wam_br = dealPrices.getDeltaName() == DeltaName.B_DELTA
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
                        cumService.incDiffFactBrFailsCount(dealPrices.getTradingMode());
                        warningLogger.warn("diff_fact_br_fails_count = " + cumService.getTotalCommon().getDiffFactBrFailsCount());
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

        cumService.addDiffFactBr(dealPrices.getTradingMode(), diffFactBr.getVal(), bitmexService.getContractType().isEth());

        // ast_diff_fact = ast_delta_fact - ast_delta
        BigDecimal ast_diff_fact = ast_delta_fact.subtract(ast_delta);
        cumService.addAstDiffFact(dealPrices.getTradingMode(), ast_diff_fact1, ast_diff_fact2, ast_diff_fact);

        cumService.setSlip(dealPrices.getTradingMode());

        //print total common
        final CumParams cumParams = cumService.getTotalCommon();
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
                diff_fact_v1_b.toPlainString(),
                diff_fact_v1_o.toPlainString(),
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
                cumParams.getSlipBr().toPlainString(), cumParams.getSlip().toPlainString()
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

    private void setAndPrintCom(DealPrices dealPrices) {
        final BigDecimal bFee = settings.getBFee(dealPrices.getBtmPlacingType());
        final BigDecimal oFee = settings.getOFee(dealPrices.getOkexPlacingType());
        final Integer modeScale = settings.getContractMode().getModeScale();

        final BigDecimal b_price_fact = dealPrices.getbPriceFact().getAvg();
        final BigDecimal ok_price_fact = dealPrices.getoPriceFact().getAvg();
        final BigDecimal con = dealPrices.getbBlock();
        // ast_com1 = con / b_price_fact * 0.075 / 100
        // ast_com2 = con / ok_price_fact * 0.015 / 100
        // ast_com = ast_com1 + ast_com2
        final BigDecimal ast_com1 = con.divide(b_price_fact, 16, RoundingMode.HALF_UP).multiply(bFee)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        final BigDecimal ast_com2 = con.divide(ok_price_fact, 16, RoundingMode.HALF_UP).multiply(oFee)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        final BigDecimal ast_com = ast_com1.add(ast_com2);
        cumService.addAstCom(dealPrices.getTradingMode(), ast_com1, ast_com2, ast_com);

        final BigDecimal com1 = b_price_fact.multiply(bFee).divide(BigDecimal.valueOf(100), 16, BigDecimal.ROUND_HALF_UP)
                .setScale(modeScale, BigDecimal.ROUND_HALF_UP);
        final BigDecimal com2 = ok_price_fact.multiply(oFee).divide(BigDecimal.valueOf(100), 16, BigDecimal.ROUND_HALF_UP)
                .setScale(modeScale, BigDecimal.ROUND_HALF_UP);

        cumService.addCom(dealPrices.getTradingMode(), com1, com2);

        // print total common
        final BigDecimal com = com1.add(com2);
        final CumParams cumParams = cumService.getTotalCommon();
        final BigDecimal cumCom = cumParams.getCumCom1().add(cumParams.getCumCom2());
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

    private void setAndPrintP2CumBitmexMCom() {

        final BigDecimal con = dealPrices.getbBlock();
        final BigDecimal b_price_fact = dealPrices.getbPriceFact().getAvg();

        // bitmex_m_com = round(open_price_fact * 0.025 / 100; 4),
        final BigDecimal BITMEX_M_COM_FACTOR = new BigDecimal(0.025);
        BigDecimal bitmexMCom = b_price_fact.multiply(BITMEX_M_COM_FACTOR)
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);

        // ast_bitmex_m_com = con / b_price_fact * 0.025 / 100
        // cum_ast_Bitmex_m_com = sum(ast_bitmex_m_com)
        final BigDecimal ast_bitmex_m_com = con.divide(b_price_fact, 16, RoundingMode.HALF_UP).multiply(BITMEX_M_COM_FACTOR)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        cumService.addBitmexMCom(dealPrices.getTradingMode(), bitmexMCom, ast_bitmex_m_com);

        // print total common
        final CumParams cumParams = cumService.getTotalCommon();
        deltaLogWriter.info(String.format("#%s bitmex_m_com=%s; cum_bitmex_m_com=%s; " +
                        "ast_bitmex_m_com=%s; cum_ast_Bitmex_m_com=%s",
                counterName,
                bitmexMCom.toPlainString(),
                cumParams.getCumBitmexMCom().toPlainString(),
                cumParams.getAstBitmexMCom().toPlainString(),
                cumParams.getCumAstBitmexMCom().toPlainString()
        ));
    }


}
