package com.bitplay.arbitrage;

import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.BTM_MODE;
import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.OK_MODE;

import com.bitplay.arbitrage.dto.DiffFactBr;
import com.bitplay.arbitrage.exceptions.ToWarningLogException;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV2;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.util.Pair;

public class DiffFactBrComputer {

    // global variables
    private PosMode pos_mode;          // задается на UI
    private int pos_bo;                // позиция до сделки
    private int pos_ao;                // позиция после сделки
    private BigDecimal b_delta_plan;
    private BigDecimal o_delta_plan;
    private BigDecimal delta_fact;
    private BordersV2 bordersV2;

    public DiffFactBrComputer(PosMode pos_mode, int pos_bo, int pos_ao, BigDecimal b_delta_plan, BigDecimal o_delta_plan, BigDecimal delta_fact,
            BordersV2 bordersV2) {
        this.pos_mode = pos_mode;
        this.pos_bo = pos_bo;
        this.pos_ao = pos_ao;
        this.b_delta_plan = b_delta_plan;
        this.o_delta_plan = o_delta_plan;
        this.delta_fact = delta_fact;
        this.bordersV2 = bordersV2;
    }

    WamBr comp_wam_br_for_ok_open_long() {
        List<BorderItem> b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .get()
                .getBorderItemList();

        return getOpenLong(b_br_open, b_delta_plan);
    }

    WamBr comp_wam_br_for_ok_open_short() {
        List<BorderItem> o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .get()
                .getBorderItemList();

        return getOpenShort(o_br_open, o_delta_plan);
    }

    WamBr comp_wam_br_for_ok_close_long() {
        List<BorderItem> o_br_close = bordersV2.getBorderTableByName("o_br_close")
                .get()
                .getBorderItemList();

        return getCloseLong(o_br_close, o_delta_plan);
    }

    WamBr comp_wam_br_for_ok_close_short() {
        List<BorderItem> b_br_close = bordersV2.getBorderTableByName("b_br_close")
                .get()
                .getBorderItemList();

        return getCloseShort(b_br_close, b_delta_plan);
    }

    WamBr comp_wam_br_for_b_open_long() {
        List<BorderItem> o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .get()
                .getBorderItemList();

        return getOpenLong(o_br_open, o_delta_plan);
    }

    WamBr comp_wam_br_for_b_open_short() {
        List<BorderItem> b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .get()
                .getBorderItemList();

        return getOpenShort(b_br_open, b_delta_plan);
    }

    WamBr comp_wam_br_for_b_close_long() {
        List<BorderItem> b_br_close = bordersV2.getBorderTableByName("b_br_close")
                .get()
                .getBorderItemList();

        return getCloseLong(b_br_close, b_delta_plan);
    }

    WamBr comp_wam_br_for_b_close_short() {
        List<BorderItem> o_br_close = bordersV2.getBorderTableByName("o_br_close")
                .get()
                .getBorderItemList();

        return getCloseShort(o_br_close, o_delta_plan);
    }

    private WamBr getOpenLong(List<BorderItem> br_open, BigDecimal delta_plan) {
        WamBr wamBr = new WamBr();

        BigDecimal value0 = br_open.get(0).getValue();
        if (delta_plan.doubleValue() >= value0.doubleValue()
                && pos_bo < br_open.get(0).getPosLongLimit()
                && pos_ao <= br_open.get(0).getPosLongLimit()) {
            wamBr.add(BigDecimal.ONE, value0);
        } else {
            int p = pos_bo;
            int vol_fact = pos_ao - pos_bo;
            if (delta_plan.doubleValue() >= value0.doubleValue()
                    && pos_bo < br_open.get(0).getPosLongLimit()
                    && pos_ao > br_open.get(0).getPosLongLimit()) {
                BigDecimal amount0 = BigDecimal.valueOf(br_open.get(0).getPosLongLimit() - p)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                wamBr.add(amount0, value0);
                p = br_open.get(0).getPosLongLimit();
                BigDecimal value1 = br_open.get(1).getValue();
                if (delta_plan.doubleValue() >= value1.doubleValue()
                        && pos_ao < br_open.get(1).getPosLongLimit()) {
                    BigDecimal amount1 = BigDecimal.valueOf(pos_ao - p)
                            .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                            .setScale(2, BigDecimal.ROUND_HALF_UP);
                    wamBr.add(amount1, value1);
                }
            }
            int b_br_open_cnt = BordersTableValidator.find_last_active_row(br_open);
            for (int i = 1; i < b_br_open_cnt; i++) {
                BigDecimal valueI = br_open.get(i).getValue();
                if (delta_plan.doubleValue() >= valueI.doubleValue()) {
                    if (pos_bo < br_open.get(i).getPosLongLimit()
                            && pos_bo > br_open.get(i - 1).getPosLongLimit()
                            && pos_ao > br_open.get(i - 1).getPosLongLimit()
                            && pos_ao < br_open.get(i).getPosLongLimit()) {
                        wamBr.add(BigDecimal.ONE, valueI);
                        break;
                    }
                    if (pos_bo < br_open.get(i).getPosLongLimit()
                            && pos_ao >= br_open.get(i).getPosLongLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(br_open.get(i).getPosLongLimit() - p)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        wamBr.add(amountI, valueI);
                        p = br_open.get(i).getPosLongLimit();
                    }
                    if (pos_bo < br_open.get(i - 1).getPosLongLimit()
                            && pos_ao > br_open.get(i - 1).getPosLongLimit()
                            && pos_ao < br_open.get(i).getPosLongLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(pos_ao - br_open.get(i - 1).getPosLongLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        wamBr.add(amountI, valueI);
                    }
                }
            }
        }

        return wamBr;
    }

    private WamBr getOpenShort(List<BorderItem> br_open, BigDecimal delta_plan) {
        WamBr wamBr = new WamBr();
        BigDecimal value0 = br_open.get(0).getValue();
        if (delta_plan.doubleValue() >= value0.doubleValue()
                && -pos_bo < br_open.get(0).getPosShortLimit()
                && -pos_ao <= br_open.get(0).getPosShortLimit()) {
            wamBr.add(BigDecimal.ONE, value0);
        } else {
            int p = -pos_bo;
            int vol_fact = pos_bo - pos_ao;
            if (delta_plan.doubleValue() >= value0.doubleValue()
                    && -pos_bo < br_open.get(0).getPosShortLimit()
                    && -pos_ao > br_open.get(0).getPosShortLimit()) { // check it ok_open_short
                BigDecimal amount0 = BigDecimal.valueOf(br_open.get(0).getPosShortLimit() - p)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                wamBr.add(amount0, value0);
                p = br_open.get(0).getPosShortLimit();
                BigDecimal value1 = br_open.get(1).getValue();
                if (delta_plan.doubleValue() >= value1.doubleValue()
                        && -pos_ao < br_open.get(1).getPosShortLimit()) {
                    BigDecimal amount1 = BigDecimal.valueOf(-pos_ao - p)
                            .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                            .setScale(2, BigDecimal.ROUND_HALF_UP);  //pos-p/vol
                    wamBr.add(amount1, value1);
                }
            }

            int o_br_open_cnt = BordersTableValidator.find_last_active_row(br_open);
            for (int i = 1; i < o_br_open_cnt; i++) {
                BigDecimal valueI = br_open.get(i).getValue();
                if (delta_plan.doubleValue() >= valueI.doubleValue()) {
                    if (-pos_bo < br_open.get(i).getPosShortLimit()
                            && -pos_bo > br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao > br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao < br_open.get(i).getPosShortLimit()) {
                        wamBr.add(BigDecimal.ONE, valueI);
                        break;
                    }
                    if (-pos_bo < br_open.get(i).getPosShortLimit()
                            && -pos_ao >= br_open.get(i).getPosShortLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(br_open.get(i).getPosShortLimit() - p)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        wamBr.add(amountI, valueI);
                        p = br_open.get(i).getPosShortLimit();
                    }
                    if (-pos_bo < br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao > br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao < br_open.get(i).getPosShortLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(-pos_ao - br_open.get(i - 1).getPosShortLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        wamBr.add(amountI, valueI);
                    }
                }
            }
        }

        return wamBr;
    }

    private WamBr getCloseLong(List<BorderItem> br_close, BigDecimal delta_plan) {
        WamBr wamBr = new WamBr();

        int k = BordersTableValidator.find_last_active_row(br_close) - 1;

        BigDecimal valueK = br_close.get(k).getValue();
        if (delta_plan.doubleValue() >= valueK.doubleValue()
                && pos_bo > br_close.get(k).getPosLongLimit()
                && pos_ao >= br_close.get(k).getPosLongLimit()) {
            wamBr.add(BigDecimal.ONE, valueK);
        } else {
            int l = pos_ao;
            int p = pos_bo;
            int vol_fact = pos_bo - pos_ao;
            if (delta_plan.doubleValue() >= valueK.doubleValue()
                    && pos_bo > br_close.get(k).getPosLongLimit()
                    && pos_ao <= br_close.get(k).getPosLongLimit()) {
                BigDecimal amountK = BigDecimal.valueOf(p - br_close.get(k).getPosLongLimit())
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                wamBr.add(amountK, valueK);
            }
            for (int i = 0; i < k; i++) {
                BigDecimal valueI = br_close.get(i).getValue();
                if (delta_plan.doubleValue() >= valueI.doubleValue()) {
                    if (pos_bo > br_close.get(i).getPosLongLimit() && pos_bo <= br_close.get(i + 1).getPosLongLimit() && pos_ao >= br_close.get(i)
                            .getPosLongLimit()) {
                        wamBr.add(BigDecimal.ONE, valueI);
                        break;
                    }
                    if (pos_bo >= br_close.get(i + 1).getPosLongLimit() && pos_ao < br_close.get(i + 1).getPosLongLimit()) {
                        BigDecimal amountI1 = BigDecimal.valueOf(br_close.get(i + 1).getPosLongLimit() - l)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        wamBr.add(amountI1, valueI);
                        l = br_close.get(i + 1).getPosLongLimit();
                    }
                    if (pos_bo > br_close.get(i).getPosLongLimit() && pos_bo < br_close.get(i + 1).getPosLongLimit() && pos_ao < br_close.get(i)
                            .getPosLongLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(pos_bo - br_close.get(i).getPosLongLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        wamBr.add(amountI, valueI);
                    }
                }
            }
        }

        return wamBr;
    }

    private WamBr getCloseShort(List<BorderItem> br_close, BigDecimal delta_plan) {
        WamBr wamBr = new WamBr();

        int k = BordersTableValidator.find_last_active_row(br_close) - 1;

        BigDecimal valueK = br_close.get(k).getValue();
        if (delta_plan.doubleValue() >= valueK.doubleValue()
                && -pos_bo > br_close.get(k).getPosShortLimit()
                && -pos_ao >= br_close.get(k).getPosShortLimit()) {
            wamBr.add(BigDecimal.ONE, valueK);
        } else {
            int l = -pos_ao;
            int p = -pos_bo;
            int vol_fact = pos_ao - pos_bo;
            if (delta_plan.doubleValue() >= valueK.doubleValue()
                    && -pos_bo > br_close.get(k).getPosShortLimit()
                    && -pos_ao <= br_close.get(k).getPosShortLimit()) {
                BigDecimal amountK = BigDecimal.valueOf(p - br_close.get(k).getPosShortLimit())
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                wamBr.add(amountK, valueK);
            }
            for (int i = 0; i < k; i++) {
                BigDecimal valueI = br_close.get(i).getValue();
                if (delta_plan.doubleValue() >= valueI.doubleValue()) {
                    if (-pos_bo > br_close.get(i).getPosShortLimit() && -pos_bo <= br_close.get(i + 1).getPosShortLimit() && -pos_ao >= br_close.get(i)
                            .getPosShortLimit()) {
                        wamBr.add(BigDecimal.ONE, valueI);
                        break;
                    }
                    if (-pos_bo >= br_close.get(i + 1).getPosShortLimit() && -pos_ao < br_close.get(i + 1).getPosShortLimit()) {
                        BigDecimal amountI1 = BigDecimal.valueOf(br_close.get(i + 1).getPosShortLimit() - l)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        wamBr.add(amountI1, valueI);
                        l = br_close.get(i + 1).getPosShortLimit();
                    }
                    if (-pos_bo > br_close.get(i).getPosShortLimit() && -pos_bo < br_close.get(i + 1).getPosShortLimit() && -pos_ao < br_close.get(i)
                            .getPosShortLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(-pos_bo - br_close.get(i).getPosShortLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        wamBr.add(amountI, valueI);
                    }
                }
            }
        }

        return wamBr;
    }

    DiffFactBr compute() throws ToWarningLogException {

        BordersTableValidator bordersTableValidator = new BordersTableValidator();
        bordersTableValidator.doCheck(bordersV2);

        WamBr wamBr;

        if (pos_mode == OK_MODE && pos_bo >= 0 && pos_ao > 0 && abs(pos_ao) > abs(pos_bo)) {
            wamBr = comp_wam_br_for_ok_open_long();
        } else if (pos_mode == OK_MODE && pos_bo <= 0 && pos_ao < 0 && abs(pos_ao) > abs(pos_bo)) {
            wamBr = comp_wam_br_for_ok_open_short();
        } else if (pos_mode == OK_MODE && pos_bo > 0 && abs(pos_ao) < abs(pos_bo)) {
            wamBr = comp_wam_br_for_ok_close_long();
        } else if (pos_mode == OK_MODE && pos_bo < 0 && abs(pos_ao) < abs(pos_bo)) {
            wamBr = comp_wam_br_for_ok_close_short();
        } else if (pos_mode == BTM_MODE && pos_bo >= 0 && pos_ao > 0 && abs(pos_ao) > abs(pos_bo)) {
            wamBr = comp_wam_br_for_b_open_long();
        } else if (pos_mode == BTM_MODE && pos_bo <= 0 && pos_ao < 0 && abs(pos_ao) > abs(pos_bo)) {
            wamBr = comp_wam_br_for_b_open_short();
        } else if (pos_mode == BTM_MODE && pos_bo > 0 && abs(pos_ao) < abs(pos_bo)) {
            wamBr = comp_wam_br_for_b_close_long();
        } else if (pos_mode == BTM_MODE && pos_bo < 0 && abs(pos_ao) < abs(pos_bo)) {
            wamBr = comp_wam_br_for_b_close_short();
        } else {
            if (pos_bo == pos_ao) {
                throw new ToWarningLogException("Error: case undefined: pos_bo == pos_ao");
            }
            throw new ToWarningLogException("Error: case undefined");
        }

        final BigDecimal diff_fact_br = delta_fact.subtract(wamBr.getValue());
        final String diffFactBrString = wamBr.getDiffFactBrString();

        if (diffFactBrString.isEmpty()) {
            throw new ToWarningLogException(String.format("Error: wam_br=0. pos_bo=%s, pos_ao=%s, pos_mode=%s, "
                            + "b_delta_plan=%s, o_delta_plan=%s, bordersTable=%s",
                    pos_bo, pos_ao, pos_mode,
                    b_delta_plan, o_delta_plan,
                    bordersV2.getBorderTableList().stream()
                            .map(BorderTable::toString)
                            .reduce((acc, item) -> acc + ", " + item)));
        }

        return new DiffFactBr(diff_fact_br,
                String.format("%s - (%s)",
                        delta_fact.toPlainString(),
                        diffFactBrString));
    }

    /**
     * weighted average mean border
     */
    private class WamBr {

        List<Pair<BigDecimal, BigDecimal>> wamBrParts = new ArrayList<>(); // amount, value

        void add(BigDecimal amount, BigDecimal value) {
            wamBrParts.add(Pair.of(amount, value));
        }

        private BigDecimal getValue() {
            BigDecimal wamBr = BigDecimal.ZERO;
            for (Pair<BigDecimal, BigDecimal> wamBrPart : wamBrParts) {
                final BigDecimal amount = wamBrPart.getFirst();
                final BigDecimal value = wamBrPart.getSecond();
                wamBr = wamBr.add(amount.multiply(value)).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            return wamBr;
        }

        private String getDiffFactBrString() {
            StringBuilder sb = new StringBuilder();
            for (Pair<BigDecimal, BigDecimal> wamBrPart : wamBrParts) {
                if (sb.length() > 0) {
                    sb.append(" + [");
                } else {
                    sb.append("[");
                }
                final BigDecimal amount = wamBrPart.getFirst();
                final BigDecimal value = wamBrPart.getSecond();
                sb.append(amount).append(" * ").append(value).append("]");
            }
            return sb.toString();
        }
    }

    private int abs(int v) {
        return v > 0 ? v : -v;
    }
}
