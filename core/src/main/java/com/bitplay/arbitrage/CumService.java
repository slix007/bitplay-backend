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

    public CumPersistenceService getCumPersistenceService() {
        return cumPersistenceService;
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

    public CumParams getTotalCommon() {
        return cumPersistenceService.fetchCum(CumType.TOTAL, CumTimeType.COMMON);
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

            //save only currently calculated fields
            // cumPersistenceService.saveCumParams(cumParams); - error with counter(vert)
            cumPersistenceService.setSlip(cumParams.getCumType(), cumParams.getCumTimeType(), slipBr, slip);
        }
    }


}
