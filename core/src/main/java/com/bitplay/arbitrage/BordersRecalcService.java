package com.bitplay.arbitrage;

import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.BorderItem;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.BorderTable;
import com.bitplay.persistance.domain.BordersV2;
import com.bitplay.persistance.domain.GuiParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
            }
            if (borderParams.getActiveVersion() == BorderParams.Ver.V2) {
                recalculateBordersV2(borderParams);
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

    private void recalculateBordersV2(BorderParams borderParams) {
        final BigDecimal b_delta = arbitrageService.getDelta1();
        final BigDecimal o_delta = arbitrageService.getDelta2();

        final BordersV2 bordersV2 = borderParams.getBordersV2();

        if (bordersV2.getAutoBaseLvl()) {
            recalcAutoBaseLvl(borderParams); // 'borderParams' will be saved after full recalculateBordersV2
//            persistenceService.saveBorderParams(borderParams);
        }

//        b_add_delta, ok_add_delta
//        mid_delta = (abs(delta1) + abs(delta2)) / 2
        final BigDecimal b_add_delta = bordersV2.getbAddDelta();
        final BigDecimal ok_add_delta = bordersV2.getOkAddDelta();
        final Integer base_lvl = bordersV2.getBaseLvlCnt();
        final Integer max_lvl = bordersV2.getMaxLvl();
        final BordersV2.BaseLvlType base_lvl_type = bordersV2.getBaseLvlType();
        final Integer step = bordersV2.getStep();
        final Integer gap_step = bordersV2.getGapStep();

        if (b_add_delta.signum() == 0 && ok_add_delta.signum() == 0) {
            return;
        }
        final BigDecimal mid_delta = ((b_delta.abs()).add(o_delta.abs())).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);

//        if b_delta >= 0
//        b_br_open_val[base_lvl] = mid_delta + b_add_delta
//        ok_br_close_val[base_lvl] = -mid_delta + ok_add_delta
//        if b_delta < 0
//        b_br_open_val[base_lvl] = -mid_delta + b_add_delta
//        ok_br_close_val[base_lvl] = mid_delta + ok_add_delta
        final BigDecimal b_baseVal = b_delta.signum() >= 0
                ? b_add_delta.add(mid_delta)        // b_br_open_val[base_lvl] = mid_delta + b_add_delta
                : b_add_delta.subtract(mid_delta);  // b_br_open_val[base_lvl] = -mid_delta + b_add_delta
        final BigDecimal ok_baseVal = b_delta.signum() >= 0
                ? ok_add_delta.subtract(mid_delta)        // ok_br_close_val[base_lvl] = -mid_delta + ok_add_delta
                : ok_add_delta.add(mid_delta);  // ok_br_close_val[base_lvl] = mid_delta + ok_add_delta
        final String debugStr = String.format("mid_delta=%s, b_baseVal=%s, ok_baseVal=%s, b_add_delta=%s, ok_add_delta=%s, base_lvl=%s",
                mid_delta, b_baseVal, ok_baseVal, b_add_delta, ok_add_delta, base_lvl);
        logger.debug(debugStr);

        if (base_lvl_type == BordersV2.BaseLvlType.B_OPEN) {
            bordersV2.getBorderTableByName("b_br_open")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            int currStep = step * (i + 1 - base_lvl); // val *_br_open:0,1,2,3
                            final BigDecimal val = b_baseVal.add(BigDecimal.valueOf(currStep));
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });
            // with gap_step
            bordersV2.getBorderTableByName("b_br_close")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            // val *_br_open:0,1,2,3 ... gap ...*_br_close:  -10,-11,-12,-13
                            int stepToZeroThat = step * (1 - base_lvl);
                            int stepToZeroThis = stepToZeroThat - gap_step;
                            int currStep = stepToZeroThis + step * (-i);
                            final BigDecimal val = b_baseVal.add(BigDecimal.valueOf(currStep));
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });

            bordersV2.getBorderTableByName("o_br_close")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            int currStep = step * (base_lvl - i - 1); // val X_br_open:0,-1,-2,-3
                            final BigDecimal val = ok_baseVal.add(BigDecimal.valueOf(currStep));
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });
            // with gap_step
            bordersV2.getBorderTableByName("o_br_open")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            // X_br_close:5,0,-5,-10 ... gap(20) ...X_br_open:  25,30,35,40
                            int stepToZeroThat = step * (base_lvl - 1);
                            int stepToZeroThis = stepToZeroThat + gap_step;
                            int currStep = stepToZeroThis + step * i;
                            final BigDecimal val = ok_baseVal.add(BigDecimal.valueOf(currStep));
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });


        } else { // base_lvl_type == BordersV2.BaseLvlType.OK_OPEN
            bordersV2.getBorderTableByName("o_br_open")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            int currStep = step * (i + 1 - base_lvl); // val *_br_open:0,1,2,3
                            final BigDecimal val = ok_baseVal.add(BigDecimal.valueOf(currStep));
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });
            // with gap_step
            bordersV2.getBorderTableByName("o_br_close")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            // val *_br_open:0,1,2,3 ... gap ...*_br_close:  -10,-11,-12,-13
                            int stepToZeroThat = step * (1 - base_lvl);
                            int stepToZeroThis = stepToZeroThat - gap_step;
                            int currStep = stepToZeroThis + step * (-i);
                            final BigDecimal val = ok_baseVal.add(BigDecimal.valueOf(currStep));
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });

            bordersV2.getBorderTableByName("b_br_close")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            int currStep = step * (base_lvl - i - 1); // val X_br_open:0,-1,-2,-3
                            final BigDecimal val = b_baseVal.add(BigDecimal.valueOf(currStep));
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });
            // with gap_step
            bordersV2.getBorderTableByName("b_br_open")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            // X_br_close:5,0,-5,-10 ... gap(20) ...X_br_open:  25,30,35,40
                            int stepToZeroThat = step * (base_lvl - 1);
                            int stepToZeroThis = stepToZeroThat + gap_step;
                            int currStep = stepToZeroThis + step * i;
                            final BigDecimal val = b_baseVal.add(BigDecimal.valueOf(currStep));
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });
        }

        persistenceService.saveBorderParams(borderParams);
    }

    private void recalcAutoBaseLvl(BorderParams borderParams) {
        recalcBaseLvlType(borderParams);
        recalcBaseLvlCnt(borderParams);
    }

    private void recalcBaseLvlType(BorderParams borderParams) {
        final BigDecimal b_pos = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal ok_pos_long = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal ok_pos_short = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
        final BigDecimal ok_pos = ok_pos_long.subtract(ok_pos_short);

        final BordersV2 bordersV2 = borderParams.getBordersV2();

        if (borderParams.getPosMode() == BorderParams.PosMode.OK_MODE) {
            if (ok_pos.signum() > 0) {
                bordersV2.setBaseLvlType(BordersV2.BaseLvlType.B_OPEN);
            } else { // if (ok_pos <= 0)
                bordersV2.setBaseLvlType(BordersV2.BaseLvlType.OK_OPEN);
            }
        } else { //BorderParams.PosMode.BTM_MODE
            if (b_pos.signum() > 0) {
                bordersV2.setBaseLvlType(BordersV2.BaseLvlType.OK_OPEN);
            } else { // if (b_pos <= 0)
                bordersV2.setBaseLvlType(BordersV2.BaseLvlType.B_OPEN);
            }
        }
    }

    private void recalcBaseLvlCnt(BorderParams borderParams) {
        final BigDecimal b_pos = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal ok_pos_long = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal ok_pos_short = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
        final BigDecimal ok_pos = ok_pos_long.subtract(ok_pos_short);

        final BordersV2 bordersV2 = borderParams.getBordersV2();

        final BorderTable b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .orElseThrow(() -> new IllegalArgumentException("no table b_br_open"));
        final BorderTable o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .orElseThrow(() -> new IllegalArgumentException("no table o_br_open"));

        int base_lvl_cnt = borderParams.getBordersV2().getBaseLvlCnt();

        if (borderParams.getPosMode() == BorderParams.PosMode.OK_MODE) {
            if (ok_pos.signum() > 0) {
                for (BorderItem borderItem : b_br_open.getBorderItemList()) {
                    if (ok_pos.compareTo(BigDecimal.valueOf(borderItem.getPosLongLimit())) < 0) {
                        base_lvl_cnt = borderItem.getId(); // первый уровень pos_long_limit, который больше ok_pos;
                        break;
                    }
                }
            } else // if (ok_pos <= 0) {
                for (BorderItem borderItem : o_br_open.getBorderItemList()) {
                    // if (abs(ok_pos) < o_br_open.pos_short_limit)
                    if ((ok_pos.abs()).compareTo(BigDecimal.valueOf(borderItem.getPosShortLimit())) < 0) {
                        base_lvl_cnt = borderItem.getId(); // первый уровень pos_short_limit, который больше abs(ok_pos);
                        break;
                    }
                }
        } else { //BorderParams.PosMode.BTM_MODE
            if (b_pos.signum() > 0) {
                for (BorderItem borderItem : o_br_open.getBorderItemList()) {
                    if (b_pos.compareTo(BigDecimal.valueOf(borderItem.getPosLongLimit())) < 0) {
                        base_lvl_cnt = borderItem.getId(); // первый уровень pos_long_limit, который больше b_pos;
                        break;
                    }
                }
            } else { //if (b_pos <= 0) {
                for (BorderItem borderItem : b_br_open.getBorderItemList()) {
                    if ((b_pos.abs()).compareTo(BigDecimal.valueOf(borderItem.getPosShortLimit())) < 0) {
                        base_lvl_cnt = borderItem.getId(); // первый уровень pos_short_limit, который больше abs(b_pos);
                        break;
                    }
                }
            }
        }

        borderParams.getBordersV2().setBaseLvlCnt(base_lvl_cnt);
        // 'borderParams' will be saved after full recalculateBordersV2
    }

}
