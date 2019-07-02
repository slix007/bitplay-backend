package com.bitplay.arbitrage;

import com.bitplay.persistance.CumPersistenceService;
import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.CumTimeType;
import com.bitplay.persistance.domain.CumType;
import com.bitplay.persistance.domain.settings.TradingMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Cumulative values service.
 */
@Service
public class CumService {

    @Autowired
    private CumPersistenceService cumPersistenceService;

    public void incCounter1(TradingMode tradingMode) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setVert1(cumParams.getVert1() + 1);
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void incCounter2(TradingMode tradingMode) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setVert2(cumParams.getVert2() + 1);
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void incCompletedCounter1(TradingMode tradingMode) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setCompletedVert1(cumParams.getCompletedVert1() + 1);
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void incCompletedCounter2(TradingMode tradingMode) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setCompletedVert2(cumParams.getCompletedVert2() + 1);
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void incUnstartedVert1(TradingMode tradingMode) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setUnstartedVert1(cumParams.getUnstartedVert1() + 1);
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void incUnstartedVert2(TradingMode tradingMode) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setUnstartedVert2(cumParams.getUnstartedVert2() + 1);
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    private List<CumParams> getCumParamsList(TradingMode tradingMode) {
        final List<CumParams> list = new ArrayList<>();
        list.add(cumPersistenceService.fetchCum(CumType.TOTAL, CumTimeType.COMMON));
        list.add(cumPersistenceService.fetchCum(CumType.TOTAL, CumTimeType.EXTENDED));
        if (tradingMode == TradingMode.CURRENT || tradingMode == TradingMode.CURRENT_VOLATILE) {
            list.add(cumPersistenceService.fetchCum(CumType.CURRENT, CumTimeType.COMMON));
            list.add(cumPersistenceService.fetchCum(CumType.CURRENT, CumTimeType.EXTENDED));
        } else {
            list.add(cumPersistenceService.fetchCum(CumType.VOLATILE, CumTimeType.COMMON));
            list.add(cumPersistenceService.fetchCum(CumType.VOLATILE, CumTimeType.EXTENDED));
        }
        return list;
    }

    public void addCumDelta(TradingMode tradingMode, BigDecimal deltaPlan) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setCumDelta(cumParams.getCumDelta().add(deltaPlan));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addAstDelta1(TradingMode tradingMode, BigDecimal ast_delta1) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setAstDelta1(ast_delta1);
            cumParams.setCumAstDelta1((cumParams.getCumAstDelta1().add(cumParams.getAstDelta1())).setScale(8, BigDecimal.ROUND_HALF_UP));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addAstDelta2(TradingMode tradingMode, BigDecimal ast_delta2) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setAstDelta2(ast_delta2);
            cumParams.setCumAstDelta2((cumParams.getCumAstDelta2().add(cumParams.getAstDelta2())).setScale(8, BigDecimal.ROUND_HALF_UP));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addAstDeltaFact1(TradingMode tradingMode, BigDecimal ast_delta1_fact) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setAstDeltaFact1(ast_delta1_fact);
            cumParams.setCumAstDeltaFact1((cumParams.getCumAstDeltaFact1().add(cumParams.getAstDeltaFact1())).setScale(8, BigDecimal.ROUND_HALF_UP));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addAstDeltaFact2(TradingMode tradingMode, BigDecimal ast_delta2_fact) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setAstDeltaFact2(ast_delta2_fact);
            cumParams.setCumAstDeltaFact2((cumParams.getCumAstDeltaFact2().add(cumParams.getAstDeltaFact2())).setScale(8, BigDecimal.ROUND_HALF_UP));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public CumParams getTotalCommon() {
        return cumPersistenceService.fetchCum(CumType.TOTAL, CumTimeType.COMMON);
    }

    public void addCumDiff2(TradingMode tradingMode, BigDecimal diff2_pre, BigDecimal diff2_post) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setCumDiff2Pre(cumParams.getCumDiff2Pre().add(diff2_pre));
            cumParams.setCumDiff2Post(cumParams.getCumDiff2Post().add(diff2_post));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addDeltaFact(TradingMode tradingMode, BigDecimal deltaFact) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setCumDeltaFact(cumParams.getCumDeltaFact().add(deltaFact));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addDiffFact(TradingMode tradingMode, BigDecimal diff_fact_v1_b, BigDecimal diff_fact_v1_o, BigDecimal diff_fact_v2) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            // diff_fact_v1
            cumParams.setCumDiffFact1(cumParams.getCumDiffFact1().add(diff_fact_v1_b));
            cumParams.setCumDiffFact2(cumParams.getCumDiffFact2().add(diff_fact_v1_o));

            // diff_fact_v2
            cumParams.setCumDiffFact(cumParams.getCumDiffFact().add(diff_fact_v2));

            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void incDiffFactBrFailsCount(TradingMode tradingMode) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setDiffFactBrFailsCount(cumParams.getDiffFactBrFailsCount() + 1);
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addDiffFactBr(TradingMode tradingMode, BigDecimal diffFactBr) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setCumDiffFactBr((cumParams.getCumDiffFactBr().add(diffFactBr)).setScale(2, BigDecimal.ROUND_HALF_UP));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addAstDiffFact(TradingMode tradingMode, BigDecimal ast_diff_fact1, BigDecimal ast_diff_fact2, BigDecimal ast_diff_fact) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setCumAstDiffFact1(cumParams.getCumAstDiffFact1().add(ast_diff_fact1));
            cumParams.setCumAstDiffFact2(cumParams.getCumAstDiffFact2().add(ast_diff_fact2));
            cumParams.setCumAstDiffFact(cumParams.getCumAstDiffFact().add(ast_diff_fact));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addCom(TradingMode tradingMode, BigDecimal com1, BigDecimal com2) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setCom1(com1);
            cumParams.setCom2(com2);
            cumParams.setCumCom1(cumParams.getCumCom1().add(com1));
            cumParams.setCumCom2(cumParams.getCumCom2().add(com2));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addAstCom(TradingMode tradingMode, BigDecimal ast_com1, BigDecimal ast_com2, BigDecimal ast_com) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setAstCom1(ast_com1);
            cumParams.setAstCom2(ast_com2);
            cumParams.setAstCom(ast_com);
            cumParams.setCumAstCom1((cumParams.getCumAstCom1().add(cumParams.getAstCom1())).setScale(8, BigDecimal.ROUND_HALF_UP));
            cumParams.setCumAstCom2((cumParams.getCumAstCom2().add(cumParams.getAstCom2())).setScale(8, BigDecimal.ROUND_HALF_UP));
            cumParams.setCumAstCom((cumParams.getCumAstCom().add(cumParams.getAstCom())).setScale(8, BigDecimal.ROUND_HALF_UP));
            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void setSlip(TradingMode tradingMode) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            // slip_br = (cum_diff_fact_br - cum_com1 - cum_com2) / (completed counts1 + completed counts2) *2
            // slip    = (cum_diff_fact - cum_com1 - cum_com2) / (completed counts1 + completed counts2) *2
            final BigDecimal cumCom = cumParams.getCumCom1().add(cumParams.getCumCom2());
            final BigDecimal slipBr = (cumParams.getCumDiffFactBr().subtract(cumCom))
                    .divide(BigDecimal.valueOf(cumParams.getCompletedVert1() + cumParams.getCompletedVert2()), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(2));
            cumParams.setSlipBr(slipBr);
            final BigDecimal slip = (cumParams.getCumDiffFact().subtract(cumCom))
                    .divide(BigDecimal.valueOf(cumParams.getCompletedVert1() + cumParams.getCompletedVert2()), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(2));
            cumParams.setSlip(slip);

            cumPersistenceService.saveCumParams(cumParams);
        }
    }

    public void addBitmexMCom(TradingMode tradingMode, BigDecimal bitmexMCom, BigDecimal ast_bitmex_m_com) {
        List<CumParams> list = getCumParamsList(tradingMode);
        for (CumParams cumParams : list) {
            cumParams.setCumBitmexMCom((cumParams.getCumBitmexMCom().add(bitmexMCom)).setScale(2, BigDecimal.ROUND_HALF_UP));

            cumParams.setAstBitmexMCom(ast_bitmex_m_com);
            cumParams.setCumAstBitmexMCom((cumParams.getCumAstBitmexMCom().add(cumParams.getAstBitmexMCom())).setScale(8, BigDecimal.ROUND_HALF_UP));

            cumPersistenceService.saveCumParams(cumParams);
        }

    }

}
