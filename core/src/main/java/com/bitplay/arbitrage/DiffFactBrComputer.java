package com.bitplay.arbitrage;

import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.BTM_MODE;
import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.OK_MODE;

import com.bitplay.arbitrage.dto.DiffFactBr;
import com.bitplay.arbitrage.exceptions.ToWarningLogException;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BordersV2;
import java.math.BigDecimal;
import java.util.List;

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

    private StringBuilder diffFactBrSb = new StringBuilder();

    BigDecimal comp_wam_br_for_ok_open_long() throws ToWarningLogException {
        List<BorderItem> b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .get()
                .getBorderItemList();

        return getOpenLong(b_br_open, b_delta_plan);
    }

    BigDecimal comp_wam_br_for_ok_open_short() throws ToWarningLogException {
        List<BorderItem> o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .get()
                .getBorderItemList();

        return getOpenShort(o_br_open, o_delta_plan);
    }

    BigDecimal comp_wam_br_for_ok_close_long() throws ToWarningLogException {
        List<BorderItem> o_br_close = bordersV2.getBorderTableByName("o_br_close")
                .get()
                .getBorderItemList();

        return getCloseLong(o_br_close, o_delta_plan);
    }

    BigDecimal comp_wam_br_for_ok_close_short() throws ToWarningLogException {
        List<BorderItem> b_br_close = bordersV2.getBorderTableByName("b_br_close")
                .get()
                .getBorderItemList();

        return getCloseShort(b_br_close, b_delta_plan);
    }

    BigDecimal comp_wam_br_for_b_open_long() throws ToWarningLogException {
        List<BorderItem> o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .get()
                .getBorderItemList();

        return getOpenLong(o_br_open, o_delta_plan);
    }

    BigDecimal comp_wam_br_for_b_open_short() throws ToWarningLogException {
        List<BorderItem> b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .get()
                .getBorderItemList();

        return getOpenShort(b_br_open, b_delta_plan);
    }

    BigDecimal comp_wam_br_for_b_close_long() throws ToWarningLogException {
        List<BorderItem> b_br_close = bordersV2.getBorderTableByName("b_br_close")
                .get()
                .getBorderItemList();

        return getCloseLong(b_br_close, b_delta_plan);
    }

    BigDecimal comp_wam_br_for_b_close_short() throws ToWarningLogException {
        List<BorderItem> o_br_close = bordersV2.getBorderTableByName("o_br_close")
                .get()
                .getBorderItemList();

        return getCloseShort(o_br_close, o_delta_plan);
    }

    private BigDecimal getOpenLong(List<BorderItem> br_open, BigDecimal delta_plan) throws ToWarningLogException {
        BigDecimal wam_br;
        BigDecimal value0 = br_open.get(0).getValue();
        if (delta_plan.doubleValue() >= value0.doubleValue()
                && pos_bo < br_open.get(0).getPosLongLimit()
                && pos_ao <= br_open.get(0).getPosLongLimit()) {
            diffFactBrSb.append("[").append(1).append(" * ").append(value0).append("]");
            wam_br = value0;
        } else {
            int p = pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_ao - pos_bo;
            if (delta_plan.doubleValue() >= value0.doubleValue()
                    && pos_bo < br_open.get(0).getPosLongLimit()
                    && pos_ao > br_open.get(0).getPosLongLimit()) {
                BigDecimal amount0 = BigDecimal.valueOf(br_open.get(0).getPosLongLimit() - p)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                diffFactBrSb.append("[")
                        .append(amount0).append(" * ").append(value0)
                        .append("]");
                wam_br = wam_br.add(amount0.multiply(value0)).setScale(2, BigDecimal.ROUND_HALF_UP);
                p = br_open.get(0).getPosLongLimit();
                BigDecimal value1 = br_open.get(1).getValue();
                if (delta_plan.doubleValue() >= value1.doubleValue()
                        && pos_ao < br_open.get(1).getPosLongLimit()) {
                    BigDecimal amount1 = BigDecimal.valueOf(pos_ao - p)
                            .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                            .setScale(2, BigDecimal.ROUND_HALF_UP);
                    diffFactBrSb.append(" + [")
                            .append(amount1).append(" * ").append(value1)
                            .append("]");
                    wam_br = wam_br.add(amount1.multiply(value1)).setScale(2, BigDecimal.ROUND_HALF_UP);
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
                        diffFactBrSb.append("[").append(1).append(" * ").append(valueI).append("]");
                        wam_br = valueI;
                        break;
                    }
                    if (pos_bo < br_open.get(i).getPosLongLimit()
                            && pos_ao >= br_open.get(i).getPosLongLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(br_open.get(i).getPosLongLimit() - p)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        diffFactBrSb.append(" + [")
                                .append(amountI).append(" * ").append(valueI)
                                .append("]");
                        wam_br = wam_br.add(amountI.multiply(valueI)).setScale(2, BigDecimal.ROUND_HALF_UP);
                        p = br_open.get(i).getPosLongLimit();
                    }
                    if (pos_bo < br_open.get(i - 1).getPosLongLimit()
                            && pos_ao > br_open.get(i - 1).getPosLongLimit()
                            && pos_ao < br_open.get(i).getPosLongLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(pos_ao - br_open.get(i - 1).getPosLongLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        diffFactBrSb.append(" + [")
                                .append(amountI).append(" * ").append(valueI)
                                .append("]");
                        wam_br = wam_br.add(amountI.multiply(valueI)).setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        if (wam_br.signum() == 0) {
            throw new ToWarningLogException(String.format("Error: wam_br=0. pos_bo=%s, pos_ao=%s, pos_mode=%s, delta_plan=%s, bordersTable=%s",
                    pos_bo, pos_ao, pos_mode,
                    delta_plan,
                    br_open.stream().map(BorderItem::toString)
                            .reduce((acc, item) -> acc + ", " + item)));
        }

        return wam_br;
    }

    private BigDecimal getOpenShort(List<BorderItem> br_open, BigDecimal delta_plan) throws ToWarningLogException {
        BigDecimal wam_br;
        BigDecimal value0 = br_open.get(0).getValue();
        if (delta_plan.doubleValue() >= value0.doubleValue()
                && -pos_bo < br_open.get(0).getPosShortLimit()
                && -pos_ao <= br_open.get(0).getPosShortLimit()) {
            diffFactBrSb.append("[").append(1).append(" * ").append(value0).append("]");
            wam_br = value0;
        } else {
            int p = -pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_bo - pos_ao;
            if (delta_plan.doubleValue() >= value0.doubleValue()
                    && -pos_bo < br_open.get(0).getPosShortLimit()
                    && -pos_ao > br_open.get(0).getPosShortLimit()) {
                BigDecimal amount0 = BigDecimal.valueOf(br_open.get(0).getPosShortLimit() - p)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                diffFactBrSb.append("[")
                        .append(amount0).append(" * ").append(value0)
                        .append("]");
                wam_br = wam_br.add(amount0.multiply(value0)).setScale(2, BigDecimal.ROUND_HALF_UP);
                p = br_open.get(0).getPosShortLimit();
                BigDecimal value1 = br_open.get(1).getValue();
                if (delta_plan.doubleValue() >= value1.doubleValue()
                        && -pos_ao < br_open.get(1).getPosShortLimit()) {
                    BigDecimal amount1 = BigDecimal.valueOf(-pos_ao - p)
                            .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                            .setScale(2, BigDecimal.ROUND_HALF_UP);  //pos-p/vol
                    diffFactBrSb.append(" + [")
                            .append(amount1).append(" * ").append(value1)
                            .append("]");
                    wam_br = wam_br.add(amount1.multiply(value1)).setScale(2, BigDecimal.ROUND_HALF_UP);
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
                        diffFactBrSb.append("[").append(1).append(" * ").append(valueI).append("]");
                        wam_br = valueI;
                        break;
                    }
                    if (-pos_bo < br_open.get(i).getPosShortLimit()
                            && -pos_ao >= br_open.get(i).getPosShortLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(br_open.get(i).getPosShortLimit() - p)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        diffFactBrSb.append(" + [")
                                .append(amountI).append(" * ").append(valueI)
                                .append("]");
                        wam_br = wam_br.add(amountI.multiply(valueI)).setScale(2, BigDecimal.ROUND_HALF_UP);
                        p = br_open.get(i).getPosShortLimit();
                    }
                    if (-pos_bo < br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao > br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao < br_open.get(i).getPosShortLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(-pos_ao - br_open.get(i - 1).getPosShortLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        diffFactBrSb.append(" + [")
                                .append(amountI).append(" * ").append(valueI)
                                .append("]");
                        wam_br = wam_br.add(amountI.multiply(valueI)).setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        if (wam_br.signum() == 0) {
            throw new ToWarningLogException(String.format("Error: wam_br=0. pos_bo=%s, pos_ao=%s, pos_mode=%s, delta_plan=%s, bordersTable=%s",
                    pos_bo, pos_ao, pos_mode,
                    delta_plan,
                    br_open.stream().map(BorderItem::toString)
                            .reduce((acc, item) -> acc + ", " + item)));
        }

        return wam_br;
    }

    private BigDecimal getCloseLong(List<BorderItem> br_close, BigDecimal delta_plan) throws ToWarningLogException {
        BigDecimal wam_br;

        int k = BordersTableValidator.find_last_active_row(br_close) - 1;

        BigDecimal valueK = br_close.get(k).getValue();
        if (delta_plan.doubleValue() >= valueK.doubleValue()
                && pos_bo > br_close.get(k).getPosLongLimit()
                && pos_ao >= br_close.get(k).getPosLongLimit()) {
            diffFactBrSb.append("[").append(1).append(" * ").append(valueK).append("]");
            wam_br = valueK;
        } else {
            int l = pos_ao;
            int p = pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_bo - pos_ao;
            if (delta_plan.doubleValue() >= valueK.doubleValue()
                    && pos_bo > br_close.get(k).getPosLongLimit()
                    && pos_ao <= br_close.get(k).getPosLongLimit()) {
                BigDecimal amountK = BigDecimal.valueOf(p - br_close.get(k).getPosLongLimit())
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                diffFactBrSb.append("[")
                        .append(amountK).append(" * ").append(valueK)
                        .append("]");
                wam_br = wam_br.add(amountK.multiply(valueK)).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            for (int i = 0; i < k; i++) {
                BigDecimal valueI = br_close.get(i).getValue();
                if (delta_plan.doubleValue() >= valueI.doubleValue()) {
                    if (pos_bo > br_close.get(i).getPosLongLimit() && pos_bo <= br_close.get(i + 1).getPosLongLimit() && pos_ao >= br_close.get(i)
                            .getPosLongLimit()) {
                        diffFactBrSb.append("[").append(1).append(" * ").append(valueI).append("]");
                        wam_br = valueI;
                        break;
                    }
                    if (pos_bo >= br_close.get(i + 1).getPosLongLimit() && pos_ao < br_close.get(i + 1).getPosLongLimit()) {
                        BigDecimal amountI1 = BigDecimal.valueOf(br_close.get(i + 1).getPosLongLimit() - l)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        diffFactBrSb.append(" + [")
                                .append(amountI1).append(" * ").append(valueI)
                                .append("]");
                        wam_br = wam_br.add(amountI1.multiply(valueI)).setScale(2, BigDecimal.ROUND_HALF_UP);
                        l = br_close.get(i + 1).getPosLongLimit();
                    }
                    if (pos_bo > br_close.get(i).getPosLongLimit() && pos_bo < br_close.get(i + 1).getPosLongLimit() && pos_ao < br_close.get(i)
                            .getPosLongLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(pos_bo - br_close.get(i).getPosLongLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        diffFactBrSb.append(" + [")
                                .append(amountI).append(" * ").append(valueI)
                                .append("]");
                        wam_br = wam_br.add(amountI.multiply(valueI)).setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        if (wam_br.signum() == 0) {
            throw new ToWarningLogException(String.format("Error: wam_br=0. pos_bo=%s, pos_ao=%s, pos_mode=%s, delta_plan=%s, bordersTable=%s",
                    pos_bo, pos_ao, pos_mode,
                    delta_plan,
                    br_close.stream().map(BorderItem::toString)
                            .reduce((acc, item) -> acc + ", " + item)));
        }

        return wam_br;
    }

    private BigDecimal getCloseShort(List<BorderItem> br_close, BigDecimal delta_plan) throws ToWarningLogException {

        int k = BordersTableValidator.find_last_active_row(br_close) - 1;

        BigDecimal wam_br;
        BigDecimal valueK = br_close.get(k).getValue();
        if (delta_plan.doubleValue() >= valueK.doubleValue()
                && -pos_bo > br_close.get(k).getPosShortLimit()
                && -pos_ao >= br_close.get(k).getPosShortLimit()) {
            diffFactBrSb.append("[").append(1).append(" * ").append(valueK).append("]");
            wam_br = valueK;
        } else {
            int l = -pos_ao;
            int p = -pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_ao - pos_bo;
            if (delta_plan.doubleValue() >= valueK.doubleValue()
                    && -pos_bo > br_close.get(k).getPosShortLimit()
                    && -pos_ao <= br_close.get(k).getPosShortLimit()) {
                BigDecimal amountK = BigDecimal.valueOf(p - br_close.get(k).getPosShortLimit())
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                diffFactBrSb.append(" + [")
                        .append(amountK).append(" * ").append(valueK)
                        .append("]");
                wam_br = wam_br.add(amountK.multiply(valueK)).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            for (int i = 0; i < k; i++) {
                BigDecimal valueI = br_close.get(i).getValue();
                if (delta_plan.doubleValue() >= valueI.doubleValue()) {
                    if (-pos_bo > br_close.get(i).getPosShortLimit() && -pos_bo <= br_close.get(i + 1).getPosShortLimit() && -pos_ao >= br_close.get(i)
                            .getPosShortLimit()) {
                        diffFactBrSb.append("[").append(1).append(" * ").append(valueI).append("]");
                        wam_br = valueI;
                        break;
                    }
                    if (-pos_bo >= br_close.get(i + 1).getPosShortLimit() && -pos_ao < br_close.get(i + 1).getPosShortLimit()) {
                        BigDecimal amountI1 = BigDecimal.valueOf(br_close.get(i + 1).getPosShortLimit() - l)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        diffFactBrSb.append(" + [")
                                .append(amountI1).append(" * ").append(valueI)
                                .append("]");
                        wam_br = wam_br.add(amountI1.multiply(valueI)).setScale(2, BigDecimal.ROUND_HALF_UP);
                        l = br_close.get(i + 1).getPosShortLimit();
                    }
                    if (-pos_bo > br_close.get(i).getPosShortLimit() && -pos_bo < br_close.get(i + 1).getPosShortLimit() && -pos_ao < br_close.get(i)
                            .getPosShortLimit()) {
                        BigDecimal amountI = BigDecimal.valueOf(-pos_bo - br_close.get(i).getPosShortLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        diffFactBrSb.append(" + [")
                                .append(amountI).append(" * ").append(valueI)
                                .append("]");
                        wam_br = wam_br.add(amountI.multiply(valueI)).setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        if (wam_br.signum() == 0) {
            throw new ToWarningLogException(String.format("Error: wam_br=0. pos_bo=%s, pos_ao=%s, pos_mode=%s, delta_plan=%s, bordersTable=%s",
                    pos_bo, pos_ao, pos_mode,
                    delta_plan,
                    br_close.stream().map(BorderItem::toString)
                            .reduce((acc, item) -> acc + ", " + item)));
        }

        return wam_br;
    }

    DiffFactBr compute() throws ToWarningLogException {

        BordersTableValidator bordersTableValidator = new BordersTableValidator();
        bordersTableValidator.doCheck(bordersV2);

        BigDecimal wam_br; // weighted average mean border

        if (pos_mode == OK_MODE && pos_bo >= 0 && pos_ao > 0 && abs(pos_ao) > abs(pos_bo)) {
            wam_br = comp_wam_br_for_ok_open_long();
        } else if (pos_mode == OK_MODE && pos_bo <= 0 && pos_ao < 0 && abs(pos_ao) > abs(pos_bo)) {
            wam_br = comp_wam_br_for_ok_open_short();
        } else if (pos_mode == OK_MODE && pos_bo > 0 && abs(pos_ao) < abs(pos_bo)) {
            wam_br = comp_wam_br_for_ok_close_long();
        } else if (pos_mode == OK_MODE && pos_bo < 0 && abs(pos_ao) < abs(pos_bo)) {
            wam_br = comp_wam_br_for_ok_close_short();
        } else if (pos_mode == BTM_MODE && pos_bo >= 0 && pos_ao > 0 && abs(pos_ao) > abs(pos_bo)) {
            wam_br = comp_wam_br_for_b_open_long();
        } else if (pos_mode == BTM_MODE && pos_bo <= 0 && pos_ao < 0 && abs(pos_ao) > abs(pos_bo)) {
            wam_br = comp_wam_br_for_b_open_short();
        } else if (pos_mode == BTM_MODE && pos_bo > 0 && abs(pos_ao) < abs(pos_bo)) {
            wam_br = comp_wam_br_for_b_close_long();
        } else if (pos_mode == BTM_MODE && pos_bo < 0 && abs(pos_ao) < abs(pos_bo)) {
            wam_br = comp_wam_br_for_b_close_short();
        } else {
            if (pos_bo == pos_ao) {
                throw new ToWarningLogException("Error: case undefined: pos_bo == pos_ao");
            }
            throw new ToWarningLogException("Error: case undefined");
        }

        BigDecimal diff_fact_br = delta_fact.subtract(wam_br);
        return new DiffFactBr(diff_fact_br,
                String.format("%s - (%s)",
                        delta_fact.toPlainString(),
                        diffFactBrSb.toString()));
    }

    private int abs(int v) {
        return v > 0 ? v : -v;
    }
}
