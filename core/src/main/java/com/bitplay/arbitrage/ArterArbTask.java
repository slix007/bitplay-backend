package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.AvgPrice;
import com.bitplay.arbitrage.dto.DealPrices;
import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.arbitrage.dto.DiffFactBr;
import com.bitplay.arbitrage.dto.RoundIsNotDoneException;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BorderParams.Ver;
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

@Slf4j
@Getter
@AllArgsConstructor
public class ArterArbTask {

    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private static final String NA = "NA";

    private final DealPrices dealPrices;
    private final SignalType signalType;
    private final GuiParams params;
    private final int counter;
    private final Settings settings;
    private final Position okPosition;
    private final BitmexService bitmexService;
    private final OkCoinService okCoinService;


    public boolean finishArbitrage() throws RoundIsNotDoneException {
        if (signalType != SignalType.AUTOMATIC || params.getLastDelta() == null || dealPrices.getBestQuotes() == null) {
            return false;
        }

        BigDecimal b_price_fact = fetchBtmFactPrice();
        BigDecimal ok_price_fact = fetchOkFactPrice();

        validateAvgPrice(dealPrices.getbPriceFact());
        validateAvgPrice(dealPrices.getoPriceFact());

        final BigDecimal con = dealPrices.getbBlock();
        final BigDecimal b_bid = dealPrices.getBestQuotes().getBid1_p();
        final BigDecimal b_ask = dealPrices.getBestQuotes().getAsk1_p();
        final BigDecimal ok_bid = dealPrices.getBestQuotes().getBid1_o();
        final BigDecimal ok_ask = dealPrices.getBestQuotes().getAsk1_o();

        deltasLogger.info(String.format("#%s Params for calc: con=%s, b_bid=%s, b_ask=%s, ok_bid=%s, ok_ask=%s, b_price_fact=%s, ok_price_fact=%s",
                counter, con, b_bid, b_ask, ok_bid, ok_ask, b_price_fact, ok_price_fact));

        if (params.getLastDelta().equals(ArbitrageService.DELTA1)) {
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
            final BigDecimal ast_delta1_fact = ((con.divide(b_price_fact, 16, RoundingMode.HALF_UP))
                    .subtract(con.divide(ok_price_fact, 16, RoundingMode.HALF_UP)))
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

        } else if (params.getLastDelta().equals(ArbitrageService.DELTA2)) {
            params.setCompletedCounter2(params.getCompletedCounter2() + 1);

            params.setCumDelta(params.getCumDelta().add(dealPrices.getDelta2Plan()));

            // ast_delta2 = -(con / ok_bid - con / b_ask)
            final BigDecimal ast_delta2 = ((con.divide(ok_bid, 16, RoundingMode.HALF_UP)).subtract(con.divide(b_ask, 16, RoundingMode.HALF_UP)))
                    .negate().setScale(8, RoundingMode.HALF_UP);
            params.setAstDelta2(ast_delta2);
            params.setCumAstDelta2((params.getCumAstDelta2().add(params.getAstDelta2())).setScale(8, BigDecimal.ROUND_HALF_UP));
            // ast_delta2_fact = -(con / ok_price_fact - con / b_price_fact)
            // cum_ast_delta_fact = sum(ast_delta_fact)
            final BigDecimal ast_delta2_fact = ((con.divide(ok_price_fact, 16, RoundingMode.HALF_UP))
                    .subtract(con.divide(b_price_fact, 16, RoundingMode.HALF_UP)))
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

        return true;
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

                bitmexService.updateAvgPrice(dealPrices.getbPriceFact());
                b_price_fact = dealPrices.getbPriceFact().getAvg(true);
                break;

            } catch (RoundIsNotDoneException e) {
                // logs are written in AvgPrice.getAvgPrice(true);
                if (attempt == maxAttempts) {
                    throw e;
                }

                try {
                    deltasLogger.info("Wait 200mc for avgPrice");
                    Thread.sleep(200);
                } catch (InterruptedException e1) {
                    log.error("Error on Wait 200mc for avgPrice", e1);
                }

            }
        }

        final Instant end = Instant.now();
        log.info(String.format("workaround: Bitmex updateAvgPrice. Attempt=%s. Time: %s",
                attempt, Duration.between(start, end).toString()));

        return b_price_fact;
    }

    private BigDecimal fetchOkFactPrice() throws RoundIsNotDoneException {

        BigDecimal ok_price_fact = BigDecimal.ZERO;
        int attempt = 0;
        int maxAttempts = 5;
        while (attempt < maxAttempts) {
            attempt++;

            try {
                ok_price_fact = dealPrices.getoPriceFact().getAvg(true);

                okCoinService.writeAvgPriceLog();
                break;

            } catch (RoundIsNotDoneException e) {
                // logs are written in AvgPrice.getAvgPrice(true);
                if (attempt == maxAttempts) {
                    throw e;
                }

                try {
//                    deltasLogger.info("Wait 200mc for avgPrice");
                    Thread.sleep(200);
                } catch (InterruptedException e1) {
                    log.error("Error on Wait 200mc for avgPrice", e1);
                }

                okCoinService.updateAvgPrice(dealPrices.getoPriceFact());
            }
        }

        return ok_price_fact;
    }


    private void validateAvgPrice(AvgPrice avgPrice) throws RoundIsNotDoneException {
        if (avgPrice.isItemsEmpty()) {
            throw new RoundIsNotDoneException(avgPrice.getMarketName() + " has no orders");
        }
    }

    private void printP3DeltaFact(BigDecimal deltaFact, String deltaFactString, BigDecimal ast_diff_fact1, BigDecimal ast_diff_fact2, BigDecimal ast_delta,
            BigDecimal ast_delta_fact, BigDecimal delta) {

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
//        final ArbUtils.DiffFactBr diffFactBr = ArbUtils.getDeltaFactBr(deltaFact, Collections.unmodifiableList(dealPrices.getBorderList()));
        DiffFactBr diffFactBr = new DiffFactBr(BigDecimal.valueOf(-99999999), NA);
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
                DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(
                        posMode,
                        pos_bo,
                        pos_ao,
                        dealPrices.getDelta1Plan(),
                        dealPrices.getDelta2Plan(),
                        deltaFact,
                        borderParams.getBordersV2());
                try {
                    diffFactBr = diffFactBrComputer.compute();
                } catch (Exception e) {
                    warningLogger.warn(e.toString());
                    deltasLogger.warn("WARNING: " + e.toString());
                    log.warn("WARNING", e);
                    params.setDiffFactBrFailsCount(params.getDiffFactBrFailsCount() + 1);
                    warningLogger.warn("diff_fact_br_fails_count = " + params.getDiffFactBrFailsCount());
                }
            }
        } else {
            String msg = "WARNING: borderParams.activeVersion" + borderParams.getActiveVersion();
            warningLogger.warn(msg);
            deltasLogger.warn(msg);
        }
        if (diffFactBr.getStr().equals(NA)) {
            String msg = "WARNING: diff_fact_br=NA=-99999999";
            warningLogger.warn(msg);
        }

        params.setCumDiffFactBr((params.getCumDiffFactBr().add(diffFactBr.getVal())).setScale(2, BigDecimal.ROUND_HALF_UP));

        // cum_ast_diff_fact1 = sum(ast_diff_fact1)
        // cum_ast_diff_fact2 = sum(ast_diff_fact2)
        // ast_diff_fact = ast_delta_fact - ast_delta
        // cum_ast_diff_fact = sum(ast_diff_fact)
        params.setCumAstDiffFact1(params.getCumAstDiffFact1().add(ast_diff_fact1));
        params.setCumAstDiffFact2(params.getCumAstDiffFact2().add(ast_diff_fact2));
        BigDecimal ast_diff_fact = ast_delta_fact.subtract(ast_delta);
        params.setCumAstDiffFact(params.getCumAstDiffFact().add(ast_diff_fact));

        // slip_br = (cum_diff_fact_br - cum_com1 - cum_com2) / (completed counts1 + completed counts2) *2
        // slip    = (cum_diff_fact - cum_com1 - cum_com2) / (completed counts1 + completed counts2) *2
        final BigDecimal cumCom = params.getCumCom1().add(params.getCumCom2());
        final BigDecimal slipBr = (params.getCumDiffFactBr().subtract(cumCom))
                .divide(BigDecimal.valueOf(params.getCompletedCounter1() + params.getCompletedCounter2()), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(2));
        params.setSlipBr(slipBr);
        final BigDecimal slip = (params.getCumDiffFact().subtract(cumCom))
                .divide(BigDecimal.valueOf(params.getCompletedCounter1() + params.getCompletedCounter2()), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(2));
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
                        "slipBr=%s, slip=%s",
                counter,
                deltaFactString,
                params.getCumDeltaFact().toPlainString(),
                dealPrices.getDiffB().val.toPlainString(),
                dealPrices.getDiffO().val.toPlainString(),
                diff_fact_v1.toPlainString(),
                deltaFact.toPlainString(), delta.toPlainString(), diff_fact_v2.toPlainString(),
                params.getCumDiffFact1().toPlainString(),
                params.getCumDiffFact2().toPlainString(),
                params.getCumDiffFact().toPlainString(),
                diffFactBr.getStr(),
                diffFactBr.getVal().toPlainString(),
                params.getCumDiffFactBr().toPlainString(),
                ast_diff_fact1.toPlainString(), ast_diff_fact2.toPlainString(), ast_delta_fact.toPlainString(), ast_delta.toPlainString(),
                ast_diff_fact.toPlainString(),
                params.getCumAstDiffFact1().toPlainString(), params.getCumAstDiffFact2().toPlainString(), params.getCumAstDiffFact().toPlainString(),
                slipBr.toPlainString(), slip.toPlainString()
        ));
    }

    private void printOAvgPrice() {
        deltasLogger.info(String.format("o_avg_price_long=%s, o_avg_price_short=%s ",
                okPosition.getPriceAvgLong(),
                okPosition.getPriceAvgShort()));
    }

    private void printCumDelta() {
        deltasLogger.info(String.format("#%s cum_delta=%s", counter, params.getCumDelta().toPlainString()));
    }

    private void printAstDeltaLogs(BigDecimal ast_delta, BigDecimal cum_ast_delta, BigDecimal ast_delta_fact, BigDecimal cum_ast_delta_fact) {
        deltasLogger.info(String.format("#%s ast_delta=%s, cum_ast_delta=%s, " +
                        "ast_delta_fact=%s, cum_ast_delta_fact=%s",
                counter,
                ast_delta.toPlainString(), cum_ast_delta.toPlainString(),
                ast_delta_fact.toPlainString(), cum_ast_delta_fact.toPlainString()));
    }

    private void printCom(DealPrices dealPrices) {
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
                .divide(ArbitrageService.OKEX_FACTOR, 8, RoundingMode.HALF_UP);
        final BigDecimal ast_com2 = con.divide(ok_price_fact, 16, RoundingMode.HALF_UP).multiply(oFee)
                .divide(ArbitrageService.OKEX_FACTOR, 8, RoundingMode.HALF_UP);
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
                counter,
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
                .divide(ArbitrageService.OKEX_FACTOR, 2, BigDecimal.ROUND_HALF_UP);

        params.setCumBitmexMCom((params.getCumBitmexMCom().add(bitmexMCom)).setScale(2, BigDecimal.ROUND_HALF_UP));
        // ast_Bitmex_m_com = con / b_price_fact * 0.025 / 100
        // cum_ast_Bitmex_m_com = sum(ast_Bitmex_m_com)
        final BigDecimal ast_Bitmex_m_com = con.divide(b_price_fact, 16, RoundingMode.HALF_UP).multiply(BITMEX_M_COM_FACTOR)
                .divide(ArbitrageService.OKEX_FACTOR, 8, RoundingMode.HALF_UP);
        params.setAstBitmexMCom(ast_Bitmex_m_com);
        params.setCumAstBitmexMCom((params.getCumAstBitmexMCom().add(params.getAstBitmexMCom())).setScale(8, BigDecimal.ROUND_HALF_UP));

        deltasLogger.info(String.format("#%s bitmex_m_com=%s; cum_bitmex_m_com=%s; " +
                        "ast_Bitmex_m_com=%s; cum_ast_Bitmex_m_com=%s",
                counter,
                bitmexMCom.toPlainString(),
                params.getCumBitmexMCom().toPlainString(),
                params.getAstBitmexMCom().toPlainString(),
                params.getCumAstBitmexMCom().toPlainString()
        ));
    }


}
