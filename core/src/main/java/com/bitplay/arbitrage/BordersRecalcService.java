package com.bitplay.arbitrage;

import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.GuiParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 2/14/18.
 */
@Service
public class BordersRecalcService {

    private static final Logger logger = LoggerFactory.getLogger(BordersService.class);
    //    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
//    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
//    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private ArbitrageService arbitrageService;

    public void recalc() {
        try {

            final BorderParams borderParams = persistenceService.fetchBorders();
            if (borderParams.getActiveVersion() == BorderParams.Ver.V1) {
                final BigDecimal sumDelta = borderParams.getBordersV1().getSumDelta();
                recalculateBordersV1(sumDelta);
            } else {
                recalculateBordersV2();
            }
        } catch (Exception e) {
            logger.error("on recalc borders: ", e);
            warningLogger.error("on recalc borders: " + e.getMessage());
        }
    }

    private void recalculateBordersV1(BigDecimal sumDelta) {
        final BigDecimal delta1 = arbitrageService.getDelta1();
        final BigDecimal delta2 = arbitrageService.getDelta2();
        final GuiParams params = arbitrageService.getParams();

        final BigDecimal two = new BigDecimal(2);
        if (sumDelta.compareTo(BigDecimal.ZERO) != 0) {
            if (delta1.compareTo(delta2) == 1) {
//            border1 = (abs(delta1) + abs(delta2)) / 2 + sum_delta / 2;
//            border2 = -((abs(delta1) + abs(delta2)) / 2 - sum_delta / 2);
                params.setBorder1(((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .add(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)));
                params.setBorder2(((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .subtract(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)).negate());
            } else {
//            border1 = -(abs(delta1) + abs(delta2)) / 2 - sum_delta / 2;
//            border2 = abs(delta1) + abs(delta2)) / 2 + sum_delta / 2;
                params.setBorder1(((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .subtract(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)).negate());
                params.setBorder2(((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .add(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)));
            }

            arbitrageService.saveParamsToDb();
        }
    }

    private void recalculateBordersV2() {

    }
}
