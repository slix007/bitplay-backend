package com.bitplay.arbitrage;

import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.BTM_MODE;
import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.OK_MODE;

import com.bitplay.arbitrage.exceptions.ToWarningLogException;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BordersV2;
import java.math.BigDecimal;
import java.util.List;

public class DiffFactBrComputer {

    // global variables
    private PosMode pos_mode;            // задается на UI
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

    BigDecimal comp_wam_br_for_ok_open_long() {
        List<BorderItem> b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .get()
                .getBorderItemList();

        BigDecimal wam_br;             // weighted average mean border

        if (b_delta_plan.compareTo(b_br_open.get(0).getValue()) >= 0
                && pos_bo < b_br_open.get(0).getPosLongLimit()
                && pos_ao <= b_br_open.get(0).getPosLongLimit()) {
            wam_br = b_br_open.get(0).getValue();
        } else {
            int p = pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_ao - pos_bo;
            if (b_delta_plan.compareTo(b_br_open.get(0).getValue()) >= 0
                    && pos_bo < b_br_open.get(0).getPosLongLimit() &&
                    pos_ao > b_br_open.get(0).getPosLongLimit()) {
                wam_br = wam_br.add(BigDecimal.valueOf(b_br_open.get(0).getPosLongLimit() - p)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .multiply(b_br_open.get(0).getValue()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                p = b_br_open.get(0).getPosLongLimit();
                if (b_delta_plan.compareTo(b_br_open.get(1).getValue()) >= 0
                        && pos_ao < b_br_open.get(1).getPosLongLimit()) {
                    wam_br = wam_br.add(BigDecimal.valueOf(pos_ao - p)
                            .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                            .multiply(b_br_open.get(1).getValue()))
                            .setScale(2, BigDecimal.ROUND_HALF_UP);
                }
            }
            int b_br_open_cnt = b_br_open.size();
            for (int i = 1; i < b_br_open_cnt; i++) {
                if (b_delta_plan.compareTo(b_br_open.get(i).getValue()) >= 0) {
                    if (pos_bo < b_br_open.get(i).getPosLongLimit()
                            && pos_bo > b_br_open.get(i - 1).getPosLongLimit()
                            && pos_ao > b_br_open.get(i - 1).getPosLongLimit()
                            && pos_ao < b_br_open.get(i).getPosLongLimit()) {

                        wam_br = b_br_open.get(i).getValue();
                        break;
                    }
                    if (pos_bo < b_br_open.get(i).getPosLongLimit()
                            && pos_ao >= b_br_open.get(i).getPosLongLimit()) {
                        //wam_br += (b_br_open[i].pos_long_lim - p) / vol_fact * b_br_open[i].value;
                        wam_br = wam_br.add((BigDecimal.valueOf(b_br_open.get(i).getPosLongLimit() - p)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP))
                                .multiply(b_br_open.get(i).getValue())
                                .setScale(2, BigDecimal.ROUND_HALF_UP));
                        p = b_br_open.get(i).getPosLongLimit();
                    }
                    if (pos_bo < b_br_open.get(i - 1).getPosLongLimit()
                            && pos_ao > b_br_open.get(i - 1).getPosLongLimit()
                            && pos_ao < b_br_open.get(i).getPosLongLimit()) {

                        wam_br = wam_br.add(BigDecimal.valueOf((pos_ao - b_br_open.get(i - 1).getPosLongLimit()))
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(b_br_open.get(i).getValue())
                                .setScale(2, BigDecimal.ROUND_HALF_UP));
                    }
                }
            }
        }

        return wam_br;
    }

    BigDecimal comp_wam_br_for_ok_open_short() {
        List<BorderItem> o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .get()
                .getBorderItemList();
        BigDecimal wam_br;             // weighted average mean border

        if (o_delta_plan.doubleValue() >= o_br_open.get(0).getValue().doubleValue()
                && -pos_bo < o_br_open.get(0).getPosShortLimit()
                && -pos_ao <= o_br_open.get(0).getPosShortLimit()) {
            wam_br = o_br_open.get(0).getValue();
        } else {
            int p = -pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_bo - pos_ao;
            if (o_delta_plan.doubleValue() >= o_br_open.get(0).getValue().doubleValue()
                    && -pos_bo < o_br_open.get(0).getPosShortLimit()
                    && -pos_ao > o_br_open.get(0).getPosShortLimit()) {
                wam_br = wam_br.add(BigDecimal.valueOf(o_br_open.get(0).getPosShortLimit() - p)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .multiply(o_br_open.get(0).getValue()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                p = o_br_open.get(0).getPosShortLimit();
                if (o_delta_plan.doubleValue() >= o_br_open.get(1).getValue().doubleValue()
                        && -pos_ao < o_br_open.get(1).getPosShortLimit()) {
                    wam_br = wam_br.add(BigDecimal.valueOf(-pos_ao - p)
                            .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                            .multiply(o_br_open.get(1).getValue()))
                            .setScale(2, BigDecimal.ROUND_HALF_UP);
                }
            }
            int o_br_open_cnt = o_br_open.size();
            for (int i = 1; i < o_br_open_cnt; i++) {
                if (o_delta_plan.doubleValue() >= o_br_open.get(i).getValue().doubleValue()) {
                    if (-pos_bo < o_br_open.get(i).getPosShortLimit()
                            && -pos_bo > o_br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao > o_br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao < o_br_open.get(i).getPosShortLimit()) {
                        wam_br = o_br_open.get(i).getValue();
                        break;
                    }
                    if (-pos_bo < o_br_open.get(i).getPosShortLimit()
                            && -pos_ao >= o_br_open.get(i).getPosShortLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(o_br_open.get(i).getPosShortLimit() - p)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(o_br_open.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        p = o_br_open.get(i).getPosShortLimit();
                    }
                    if (-pos_bo < o_br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao > o_br_open.get(i - 1).getPosShortLimit()
                            && -pos_ao < o_br_open.get(i).getPosShortLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(-pos_ao - o_br_open.get(i - 1).getPosShortLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(o_br_open.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        return wam_br;
    }

    BigDecimal comp_wam_br_for_ok_close_long() {
        List<BorderItem> o_br_close = bordersV2.getBorderTableByName("o_br_close")
                .get()
                .getBorderItemList();

        BigDecimal wam_br;             // weighted average mean border

        int k = BordersTableValidator.find_last_active_row(o_br_close);
        if (o_delta_plan.doubleValue() >= o_br_close.get(k).getValue().doubleValue()
                && pos_bo > o_br_close.get(k).getPosLongLimit()
                && pos_ao >= o_br_close.get(k).getPosLongLimit()) {
            wam_br = o_br_close.get(k).getValue();
        } else {
            int l = pos_ao;
            int p = pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_bo - pos_ao;
            if (o_delta_plan.doubleValue() >= o_br_close.get(k).getValue().doubleValue()
                    && pos_bo > o_br_close.get(k).getPosLongLimit()
                    && pos_ao <= o_br_close.get(k).getPosLongLimit()) {
                wam_br = wam_br.add(BigDecimal.valueOf(p - o_br_close.get(k).getPosLongLimit())
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .multiply(o_br_close.get(k).getValue()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                p = o_br_close.get(k).getPosLongLimit();
            }
            for (int i = 0; i < k; i++) {
                if (o_delta_plan.doubleValue() >= o_br_close.get(i).getValue().doubleValue()) {
                    if (pos_bo > o_br_close.get(i).getPosLongLimit() && pos_bo <= o_br_close.get(i + 1).getPosLongLimit() && pos_ao >= o_br_close.get(i)
                            .getPosLongLimit()) {
                        wam_br = o_br_close.get(i).getValue();
                        break;
                    }
                    if (pos_bo >= o_br_close.get(i + 1).getPosLongLimit() && pos_ao < o_br_close.get(i + 1).getPosLongLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(o_br_close.get(i + 1).getPosLongLimit() - l)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(o_br_close.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        l = o_br_close.get(i + 1).getPosLongLimit();
                    }
                    if (pos_bo > o_br_close.get(i).getPosLongLimit() && pos_bo < o_br_close.get(i + 1).getPosLongLimit() && pos_ao < o_br_close.get(i)
                            .getPosLongLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(pos_bo - o_br_close.get(i).getPosLongLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(o_br_close.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        return wam_br;
    }

    BigDecimal comp_wam_br_for_ok_close_short() {
        List<BorderItem> b_br_close = bordersV2.getBorderTableByName("b_br_close")
                .get()
                .getBorderItemList();

        BigDecimal wam_br;             // weighted average mean border

        int k = BordersTableValidator.find_last_active_row(b_br_close);
        //int k = find_last_active_row(b_br_close[]);
        if (b_delta_plan.doubleValue() >= b_br_close.get(k).getValue().doubleValue()
                && -pos_bo > b_br_close.get(k).getPosShortLimit()
                && -pos_ao >= b_br_close.get(k).getPosShortLimit()) {
            wam_br = b_br_close.get(k).getValue();
        } else {
            int l = -pos_ao;
            int p = -pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_ao - pos_bo;
            if (b_delta_plan.doubleValue() >= b_br_close.get(k).getValue().doubleValue()
                    && -pos_bo > b_br_close.get(k).getPosShortLimit()
                    && -pos_ao <= b_br_close.get(k).getPosShortLimit()) {
                wam_br = wam_br.add(BigDecimal.valueOf(p - b_br_close.get(k).getPosShortLimit())
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .multiply(b_br_close.get(k).getValue()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                p = b_br_close.get(k).getPosShortLimit();
            }
            for (int i = 0; i < k; i++) {
                if (b_delta_plan.doubleValue() >= b_br_close.get(i).getValue().doubleValue()) {
                    if (-pos_bo > b_br_close.get(i).getPosShortLimit() && -pos_bo <= b_br_close.get(i + 1).getPosShortLimit() && -pos_ao >= b_br_close.get(i)
                            .getPosShortLimit()) {
                        wam_br = b_br_close.get(i).getValue();
                        break;
                    }
                    if (-pos_bo >= b_br_close.get(i + 1).getPosShortLimit() && -pos_ao < b_br_close.get(i + 1).getPosShortLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(b_br_close.get(i + 1).getPosShortLimit() - l)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(b_br_close.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        l = b_br_close.get(i + 1).getPosShortLimit();
                    }
                    if (-pos_bo > b_br_close.get(i).getPosShortLimit() && -pos_bo < b_br_close.get(i + 1).getPosShortLimit() && -pos_ao < b_br_close.get(i)
                            .getPosShortLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(-pos_bo - b_br_close.get(i).getPosShortLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(b_br_close.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        return wam_br;
    }

    BigDecimal comp_wam_br_for_b_open_long() {
        List<BorderItem> o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .get()
                .getBorderItemList();

        BigDecimal wam_br;             // weighted average mean border

        if (o_delta_plan.doubleValue() >= o_br_open.get(0).getValue().doubleValue()
                && pos_bo < o_br_open.get(0).getPosLongLimit()
                && pos_ao <= o_br_open.get(0).getPosLongLimit()) {
            wam_br = o_br_open.get(0).getValue();
        } else {
            int p = pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_ao - pos_bo;
            if (o_delta_plan.doubleValue() >= o_br_open.get(0).getValue().doubleValue()
                    && pos_bo < o_br_open.get(0).getPosLongLimit()
                    && pos_ao > o_br_open.get(0).getPosLongLimit()) {
                wam_br = wam_br.add(BigDecimal.valueOf(o_br_open.get(0).getPosLongLimit() - p)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .multiply(o_br_open.get(0).getValue()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                p = o_br_open.get(0).getPosLongLimit();
                if (o_delta_plan.doubleValue() >= o_br_open.get(1).getValue().doubleValue()
                        && pos_ao < o_br_open.get(1).getPosLongLimit()) {
                    wam_br = wam_br.add(BigDecimal.valueOf(pos_ao - p)
                            .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                            .multiply(o_br_open.get(1).getValue()))
                            .setScale(2, BigDecimal.ROUND_HALF_UP);
                }
            }
            int o_br_open_cnt = BordersTableValidator.find_last_active_row(o_br_open);
//            o_br_open_cnt = sizeof(o_br_open[]);
            for (int i = 1; i < o_br_open_cnt; i++) {
                if (o_delta_plan.doubleValue() >= o_br_open.get(i).getValue().doubleValue()) {
                    if (pos_bo < o_br_open.get(i).getPosLongLimit() && pos_bo > o_br_open.get(i - 1).getPosLongLimit() && pos_ao > o_br_open.get(i - 1)
                            .getPosLongLimit()
                            && pos_ao < o_br_open.get(i).getPosLongLimit()) {
                        wam_br = o_br_open.get(i).getValue();
                        break;
                    }
                    if (pos_bo < o_br_open.get(i).getPosLongLimit() && pos_ao >= o_br_open.get(i).getPosLongLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(o_br_open.get(i).getPosLongLimit() - p)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(o_br_open.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        p = o_br_open.get(i).getPosLongLimit();
                    }
                    if (pos_bo < o_br_open.get(i - 1).getPosLongLimit() && pos_ao > o_br_open.get(i - 1).getPosLongLimit() && pos_ao < o_br_open.get(i)
                            .getPosLongLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(pos_ao - o_br_open.get(i - 1).getPosLongLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(o_br_open.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        return wam_br;
    }

    BigDecimal comp_wam_br_for_b_open_short() {
        List<BorderItem> b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .get()
                .getBorderItemList();

        BigDecimal wam_br;             // weighted average mean border

        if (b_delta_plan.doubleValue() >= b_br_open.get(0).getValue().doubleValue()
                && -pos_bo < b_br_open.get(0).getPosShortLimit()
                && -pos_ao <= b_br_open.get(0).getPosShortLimit()) {
            wam_br = b_br_open.get(0).getValue();
        } else {
            int p = -pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_bo - pos_ao;
            if (b_delta_plan.doubleValue() >= b_br_open.get(0).getValue().doubleValue()
                    && -pos_bo < b_br_open.get(0).getPosShortLimit()
                    && -pos_ao > b_br_open.get(0).getPosShortLimit()) {
                wam_br = wam_br.add(BigDecimal.valueOf(b_br_open.get(0).getPosShortLimit() - p)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .multiply(b_br_open.get(0).getValue()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                p = b_br_open.get(0).getPosShortLimit();
                if (b_delta_plan.doubleValue() >= b_br_open.get(1).getValue().doubleValue()
                        && -pos_ao < b_br_open.get(1).getPosShortLimit()) {
                    wam_br = wam_br.add(BigDecimal.valueOf(-pos_ao - p)
                            .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                            .multiply(b_br_open.get(1).getValue()))
                            .setScale(2, BigDecimal.ROUND_HALF_UP);
                }
            }
            int b_br_open_cnt = BordersTableValidator.find_last_active_row(b_br_open);
//            b_br_open_cnt = sizeof(b_br_open[]);
            for (int i = 1; i < b_br_open_cnt; i++) {
                if (b_delta_plan.doubleValue() >= b_br_open.get(i).getValue().doubleValue()) {
                    if (-pos_bo < b_br_open.get(i).getPosShortLimit() && -pos_bo > b_br_open.get(i - 1).getPosShortLimit() && -pos_ao > b_br_open.get(i - 1)
                            .getPosShortLimit()
                            && -pos_ao < b_br_open.get(i).getPosShortLimit()) {
                        wam_br = b_br_open.get(i).getValue();
                        break;
                    }
                    if (-pos_bo < b_br_open.get(i).getPosShortLimit() && -pos_ao >= b_br_open.get(i).getPosShortLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(b_br_open.get(i).getPosShortLimit() - p)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(b_br_open.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        p = b_br_open.get(i).getPosShortLimit();
                    }
                    if (-pos_bo < b_br_open.get(i - 1).getPosShortLimit() && -pos_ao > b_br_open.get(i - 1).getPosShortLimit() && -pos_ao < b_br_open.get(i)
                            .getPosShortLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(-pos_ao - b_br_open.get(i - 1).getPosShortLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(b_br_open.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        return wam_br;
    }

    BigDecimal comp_wam_br_for_b_close_long() {
        List<BorderItem> b_br_close = bordersV2.getBorderTableByName("b_br_close")
                .get()
                .getBorderItemList();

        BigDecimal wam_br;             // weighted average mean border

        int k = BordersTableValidator.find_last_active_row(b_br_close);
//        int k = find_last_active_row(b_br_close[]);
        if (b_delta_plan.doubleValue() >= b_br_close.get(k).getValue().doubleValue()
                && pos_bo > b_br_close.get(k).getPosLongLimit()
                && pos_ao >= b_br_close.get(k).getPosLongLimit()) {
            wam_br = b_br_close.get(k).getValue();
        } else {
            int l = pos_ao;
            int p = pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_bo - pos_ao;
            if (b_delta_plan.doubleValue() >= b_br_close.get(k).getValue().doubleValue()
                    && pos_bo > b_br_close.get(k).getPosLongLimit()
                    && pos_ao <= b_br_close.get(k).getPosLongLimit()) {
                wam_br = wam_br.add(BigDecimal.valueOf(p - b_br_close.get(k).getPosLongLimit())
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .multiply(b_br_close.get(k).getValue()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                p = b_br_close.get(k).getPosLongLimit();
            }
            for (int i = 0; i < k; i++) {
                if (b_delta_plan.doubleValue() >= b_br_close.get(i).getValue().doubleValue()) {
                    if (pos_bo > b_br_close.get(i).getPosLongLimit() && pos_bo <= b_br_close.get(i + 1).getPosLongLimit() && pos_ao >= b_br_close.get(i)
                            .getPosLongLimit()) {
                        wam_br = b_br_close.get(i).getValue();
                        break;
                    }
                    if (pos_bo >= b_br_close.get(i + 1).getPosLongLimit() && pos_ao < b_br_close.get(i + 1).getPosLongLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(b_br_close.get(i + 1).getPosLongLimit() - l)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(b_br_close.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        l = b_br_close.get(i + 1).getPosLongLimit();
                    }
                    if (pos_bo > b_br_close.get(i).getPosLongLimit() && pos_bo < b_br_close.get(i + 1).getPosLongLimit() && pos_ao < b_br_close.get(i)
                            .getPosLongLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(pos_bo - b_br_close.get(i).getPosLongLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(b_br_close.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        return wam_br;
    }

    BigDecimal comp_wam_br_for_b_close_short() {
        List<BorderItem> o_br_close = bordersV2.getBorderTableByName("o_br_close")
                .get()
                .getBorderItemList();
        BigDecimal wam_br;             // weighted average mean border

        int k = BordersTableValidator.find_last_active_row(o_br_close);
//        int k = find_last_active_row(o_br_close[]);
        if (o_delta_plan.doubleValue() >= o_br_close.get(k).getValue().doubleValue()
                && -pos_bo > o_br_close.get(k).getPosShortLimit()
                && -pos_ao >= o_br_close.get(k).getPosShortLimit()) {
            wam_br = o_br_close.get(k).getValue();
        } else {
            int l = -pos_ao;
            int p = -pos_bo;
            wam_br = BigDecimal.ZERO;
            int vol_fact = pos_ao - pos_bo;
            if (o_delta_plan.doubleValue() >= o_br_close.get(k).getValue().doubleValue()
                    && -pos_bo > o_br_close.get(k).getPosShortLimit()
                    && -pos_ao <= o_br_close.get(k).getPosShortLimit()) {
                wam_br = wam_br.add(BigDecimal.valueOf(p - o_br_close.get(k).getPosShortLimit())
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .multiply(o_br_close.get(k).getValue()))
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                p = o_br_close.get(k).getPosShortLimit();
            }
            for (int i = 0; i < k; i++) {
                if (o_delta_plan.doubleValue() >= o_br_close.get(i).getValue().doubleValue()) {
                    if (-pos_bo > o_br_close.get(i).getPosShortLimit() && -pos_bo <= o_br_close.get(i + 1).getPosShortLimit() && -pos_ao >= o_br_close.get(i)
                            .getPosShortLimit()) {
                        wam_br = o_br_close.get(i).getValue();
                        break;
                    }
                    if (-pos_bo >= o_br_close.get(i + 1).getPosShortLimit() && -pos_ao < o_br_close.get(i + 1).getPosShortLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(o_br_close.get(i + 1).getPosShortLimit() - l)
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(o_br_close.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                        l = o_br_close.get(i + 1).getPosShortLimit();
                    }
                    if (-pos_bo > o_br_close.get(i).getPosShortLimit() && -pos_bo < o_br_close.get(i + 1).getPosShortLimit() && -pos_ao < o_br_close.get(i)
                            .getPosShortLimit()) {
                        wam_br = wam_br.add(BigDecimal.valueOf(-pos_bo - o_br_close.get(i).getPosShortLimit())
                                .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                                .multiply(o_br_close.get(i).getValue()))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);
                    }
                }
            }
        }

        return wam_br;
    }

    BigDecimal compute() throws ToWarningLogException {

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
            throw new ToWarningLogException("Error: case undefined");
        }

        return delta_fact.subtract(wam_br); // diff_fact_br
    }

    private int abs(int v) {
        return v > 0 ? v : -v;
    }
}
