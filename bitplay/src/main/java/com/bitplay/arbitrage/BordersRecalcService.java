package com.bitplay.arbitrage;

import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV2;
import com.bitplay.persistance.domain.fluent.DeltaName;
import lombok.Getter;
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
@Getter
public class BordersRecalcService {

    private static final Logger logger = LoggerFactory.getLogger(BordersRecalcService.class);
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private DeltasCalcService deltasCalcService;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private BordersService bordersService;

    public boolean isRecalcEveryNewDelta() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        return borderParams.getBorderDelta().getDeltaCalcType().isEveryNewDelta();
    }

    public void newDeltaAdded() {
        if (isRecalcEveryNewDelta()) {
            recalc();
        }
    }

    public void recalc() {
        long startMs = System.nanoTime();
        try {
            final BorderParams borderParams = persistenceService.fetchBorders();
            final BigDecimal instantDelta1 = arbitrageService.getDelta1();
            final BigDecimal instantDelta2 = arbitrageService.getDelta2();

            BigDecimal b_delta = deltasCalcService.calcDelta(DeltaName.B_DELTA, instantDelta1);
            BigDecimal o_delta = deltasCalcService.calcDelta(DeltaName.O_DELTA, instantDelta2);

            if (b_delta == null || o_delta == null || b_delta.equals(DeltasCalcService.NONE_VALUE) || o_delta.equals(DeltasCalcService.NONE_VALUE)) {
                // Not initialized (or Error?) -> Do Nothing!
            } else {
                if (borderParams.getActiveVersion() == BorderParams.Ver.V1) {
                    final BigDecimal sumDelta = borderParams.getBordersV1().getSumDelta();
                    recalculateBordersV1(sumDelta, b_delta, o_delta);
                }
                if (borderParams.getActiveVersion() == BorderParams.Ver.V2) {
                    recalculateBordersV2(borderParams, b_delta, o_delta);
                }
            }
        } catch (Exception e) {
            logger.error("on recalc borders: ", e);
            warningLogger.error("on recalc borders: " + e.getMessage());
        } finally {
            long endMs = System.nanoTime();
            arbitrageService.getDeltaMon().setBorderMs((endMs - startMs) / 1000);
        }
    }

    private void recalculateBordersV1(BigDecimal sumDelta, BigDecimal b_delta, BigDecimal o_delta) {

        final GuiParams params = arbitrageService.getParams();

        final BigDecimal two = new BigDecimal(2);
        if (sumDelta.compareTo(BigDecimal.ZERO) != 0) {
            if (b_delta.compareTo(o_delta) == 1) {
//            border1 = (abs(b_delta) + abs(o_delta)) / 2 + sum_delta / 2;
//            border2 = -((abs(b_delta) + abs(o_delta)) / 2 - sum_delta / 2);
                params.setBorder1(((b_delta.abs().add(o_delta.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .add(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)));
                params.setBorder2(((b_delta.abs().add(o_delta.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .subtract(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)).negate());
            } else {
//            border1 = -(abs(b_delta) + abs(o_delta)) / 2 - sum_delta / 2;
//            border2 = abs(b_delta) + abs(o_delta)) / 2 + sum_delta / 2;
                params.setBorder1(((b_delta.abs().add(o_delta.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .subtract(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)).negate());
                params.setBorder2(((b_delta.abs().add(o_delta.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .add(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)));
            }

            arbitrageService.saveParamsToDb();
        }
    }

    @SuppressWarnings("Duplicates")
    private void recalculateBordersV2(BorderParams borderParams, BigDecimal b_delta, BigDecimal o_delta) {
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
        final BigDecimal step = bordersV2.getStep();
        final BigDecimal gap_step = bordersV2.getGapStep();

        if (b_add_delta.signum() == 0 && ok_add_delta.signum() == 0) {
            return;
        }
        final BigDecimal mid_delta = arbitrageService.isEth()
                ? ((b_delta.abs()).add(o_delta.abs())).divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP)
                : ((b_delta.abs()).add(o_delta.abs())).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);

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

        if (base_lvl_type == BordersV2.BaseLvlType.LEFT_OPEN) {
            bordersV2.getBorderTableByName("b_br_open")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            final BigDecimal currStep = step.multiply(BigDecimal.valueOf(i + 1 - base_lvl)); // val *_br_open:0,1,2,3
                            final BigDecimal val = b_baseVal.add(currStep);
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                            if (i + 1 == items.size()) {
                                items.get(i).setId(0);
                            }
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
                            final BigDecimal stepToZeroThat = step.multiply(BigDecimal.valueOf(1 - base_lvl));
                            final BigDecimal stepToZeroThis = stepToZeroThat.subtract(gap_step);
                            final BigDecimal currStep = stepToZeroThis.add(step.multiply(BigDecimal.valueOf((-i))));
                            final BigDecimal val = b_baseVal.add(currStep);
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                            if (i + 1 == items.size()) {
                                items.get(i).setId(0);
                            }
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });

            bordersV2.getBorderTableByName("o_br_close")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            final BigDecimal currStep = step.multiply(BigDecimal.valueOf(base_lvl - i - 1)); // val X_br_open:0,-1,-2,-3
                            final BigDecimal val = ok_baseVal.add(currStep);
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                            if (i + 1 == items.size()) {
                                items.get(i).setId(0);
                            }
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
                            final BigDecimal stepToZeroThat = step.multiply(BigDecimal.valueOf(base_lvl - 1));
                            final BigDecimal stepToZeroThis = stepToZeroThat.add(gap_step);
                            final BigDecimal currStep = stepToZeroThis.add(step.multiply(BigDecimal.valueOf(i)));
                            final BigDecimal val = ok_baseVal.add(currStep);
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                            if (i + 1 == items.size()) {
                                items.get(i).setId(0);
                            }
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });


        } else { // base_lvl_type == BordersV2.BaseLvlType.OK_OPEN
            bordersV2.getBorderTableByName("o_br_open")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            final BigDecimal currStep = step.multiply(BigDecimal.valueOf(i + 1 - base_lvl)); // val *_br_open:0,1,2,3
                            final BigDecimal val = ok_baseVal.add(currStep);
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                            if (i + 1 == items.size()) {
                                items.get(i).setId(0);
                            }
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
                            final BigDecimal stepToZeroThat = step.multiply(BigDecimal.valueOf(1 - base_lvl));
                            final BigDecimal stepToZeroThis = stepToZeroThat.subtract(gap_step);
                            final BigDecimal currStep = stepToZeroThis.add(step.multiply(BigDecimal.valueOf(-i)));
                            final BigDecimal val = ok_baseVal.add(currStep);
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                            if (i + 1 == items.size()) {
                                items.get(i).setId(0);
                            }
                        }
                        items.stream().skip(max_lvl)
                                .forEach(item -> item.setId(0));
                    });

            bordersV2.getBorderTableByName("b_br_close")
                    .ifPresent(borderTable -> {
                        final List<BorderItem> items = borderTable.getBorderItemList();
                        for (int i = 0; i < max_lvl && i < items.size(); i++) {
                            final BigDecimal currStep = step.multiply(BigDecimal.valueOf(base_lvl - i - 1)); // val X_br_open:0,-1,-2,-3
                            final BigDecimal val = b_baseVal.add(currStep);
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                            if (i + 1 == items.size()) {
                                items.get(i).setId(0);
                            }
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
                            final BigDecimal stepToZeroThat = step.multiply(BigDecimal.valueOf(base_lvl - 1));
                            final BigDecimal stepToZeroThis = stepToZeroThat.add(gap_step);
                            final BigDecimal currStep = stepToZeroThis.add(step.multiply(BigDecimal.valueOf(i)));
                            final BigDecimal val = b_baseVal.add(currStep);
                            items.get(i).setValue(val);
                            items.get(i).setId(i + 1);
                            if (i + 1 == items.size()) {
                                items.get(i).setId(0);
                            }
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

    @SuppressWarnings("Duplicates")
    private void recalcBaseLvlType(BorderParams borderParams) {
        final BigDecimal b_pos = arbitrageService.getLeftMarketService().getPosVal();
        final BigDecimal ok_pos = arbitrageService.getRightMarketService().getPosVal();

        final BordersV2 bordersV2 = borderParams.getBordersV2();

        if (borderParams.getPosMode() == BorderParams.PosMode.RIGHT_MODE) {
            if (ok_pos.signum() > 0) {
                bordersV2.setBaseLvlType(BordersV2.BaseLvlType.LEFT_OPEN);
            } else { // if (ok_pos <= 0)
                bordersV2.setBaseLvlType(BordersV2.BaseLvlType.RIGHT_OPEN);
            }
        } else { //BorderParams.PosMode.BTM_MODE
            if (b_pos.signum() > 0) {
                bordersV2.setBaseLvlType(BordersV2.BaseLvlType.RIGHT_OPEN);
            } else { // if (b_pos <= 0)
                bordersV2.setBaseLvlType(BordersV2.BaseLvlType.LEFT_OPEN);
            }
        }
    }

    @SuppressWarnings("Duplicates")
    private void recalcBaseLvlCnt(BorderParams borderParams) {
        final BigDecimal b_pos = arbitrageService.getLeftMarketService().getPosVal();
        final BigDecimal ok_pos = arbitrageService.getRightMarketService().getPosVal();

        final BordersV2 bordersV2 = borderParams.getBordersV2();

        final BorderTable b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .orElseThrow(() -> new IllegalArgumentException("no table b_br_open"));
        final BorderTable o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .orElseThrow(() -> new IllegalArgumentException("no table o_br_open"));

        int base_lvl_cnt = borderParams.getBordersV2().getBaseLvlCnt();

        if (borderParams.getPosMode() == BorderParams.PosMode.RIGHT_MODE) {
            if (ok_pos.signum() > 0) {
                for (BorderItem borderItem : b_br_open.getBorderItemList()) {
                    int posLongLimit = bordersService.usdToCont(borderItem.getPosLongLimit());
                    if (ok_pos.compareTo(BigDecimal.valueOf(posLongLimit)) < 0) {
                        base_lvl_cnt = borderItem.getId(); // первый уровень pos_long_limit, который больше ok_pos;
                        break;
                    }
                }
            } else // if (ok_pos <= 0) {
                for (BorderItem borderItem : o_br_open.getBorderItemList()) {
                    // if (abs(ok_pos) < o_br_open.pos_short_limit)
                    int posShortLimit = bordersService.usdToCont(borderItem.getPosShortLimit());
                    if ((ok_pos.abs()).compareTo(BigDecimal.valueOf(posShortLimit)) < 0) {
                        base_lvl_cnt = borderItem.getId(); // первый уровень pos_short_limit, который больше abs(ok_pos);
                        break;
                    }
                }
        } else { //BorderParams.PosMode.BTM_MODE
            if (b_pos.signum() > 0) {
                for (BorderItem borderItem : o_br_open.getBorderItemList()) {
                    int posLongLimit = bordersService.usdToCont(borderItem.getPosLongLimit());
                    if (b_pos.compareTo(BigDecimal.valueOf(posLongLimit)) < 0) {
                        base_lvl_cnt = borderItem.getId(); // первый уровень pos_long_limit, который больше b_pos;
                        break;
                    }
                }
            } else { //if (b_pos <= 0) {
                for (BorderItem borderItem : b_br_open.getBorderItemList()) {
                    int posShortLimit = bordersService.usdToCont(borderItem.getPosShortLimit());
                    if ((b_pos.abs()).compareTo(BigDecimal.valueOf(posShortLimit)) < 0) {
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
