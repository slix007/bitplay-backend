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
    private boolean isEth;
    private BigDecimal cm;

    public DiffFactBrComputer(PosMode pos_mode, int pos_bo, int pos_ao, BigDecimal b_delta_plan, BigDecimal o_delta_plan, BigDecimal delta_fact,
            BordersV2 bordersV2, boolean isEth, BigDecimal cm) {
        this.pos_mode = pos_mode;
        this.pos_bo = pos_bo;
        this.pos_ao = pos_ao;
        this.b_delta_plan = b_delta_plan;
        this.o_delta_plan = o_delta_plan;
        this.delta_fact = delta_fact;
        this.bordersV2 = bordersV2;
        this.isEth = isEth;
        this.cm = cm;
    }

    DiffFactBrComputer(PosMode pos_mode, int pos_bo, int pos_ao, BigDecimal b_delta_plan, BigDecimal o_delta_plan, BigDecimal delta_fact,
            BordersV2 bordersV2) {
        this.pos_mode = pos_mode;
        this.pos_bo = pos_bo;
        this.pos_ao = pos_ao;
        this.b_delta_plan = b_delta_plan;
        this.o_delta_plan = o_delta_plan;
        this.delta_fact = delta_fact;
        this.bordersV2 = bordersV2;
        this.isEth = false;
        this.cm = BigDecimal.valueOf(100);
    }

    private int usdToCont(int limInUsd) {
        return BordersService.usdToCont(limInUsd, pos_mode, isEth, cm);
    }

    WamBr comp_wam_br_for_ok_open_long() throws ToWarningLogException {
        List<BorderItem> b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .get()
                .getBorderItemList();

        return calcByOpenTable(b_br_open, b_delta_plan, pos_bo, pos_ao);
    }

    WamBr comp_wam_br_for_ok_open_short() throws ToWarningLogException {
        List<BorderItem> o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .get()
                .getBorderItemList();

        return calcByOpenTable(o_br_open, o_delta_plan, -pos_bo, -pos_ao);
    }

    WamBr comp_wam_br_for_ok_close_long() throws ToWarningLogException {
        List<BorderItem> o_br_close = bordersV2.getBorderTableByName("o_br_close")
                .get()
                .getBorderItemList();

        return calcByCloseTable(o_br_close, o_delta_plan, pos_bo, pos_ao);
    }

    WamBr comp_wam_br_for_ok_close_short() throws ToWarningLogException {
        List<BorderItem> b_br_close = bordersV2.getBorderTableByName("b_br_close")
                .get()
                .getBorderItemList();

        return calcByCloseTable(b_br_close, b_delta_plan, -pos_bo, -pos_ao);
    }

    WamBr comp_wam_br_for_b_open_long() throws ToWarningLogException {
        List<BorderItem> o_br_open = bordersV2.getBorderTableByName("o_br_open")
                .get()
                .getBorderItemList();

        return calcByOpenTable(o_br_open, o_delta_plan, pos_bo, pos_ao);
    }

    WamBr comp_wam_br_for_b_open_short() throws ToWarningLogException {
        List<BorderItem> b_br_open = bordersV2.getBorderTableByName("b_br_open")
                .get()
                .getBorderItemList();

        return calcByOpenTable(b_br_open, b_delta_plan, -pos_bo, -pos_ao);
    }

    WamBr comp_wam_br_for_b_close_long() throws ToWarningLogException {
        List<BorderItem> b_br_close = bordersV2.getBorderTableByName("b_br_close")
                .get()
                .getBorderItemList();

        return calcByCloseTable(b_br_close, b_delta_plan, pos_bo, pos_ao);
    }

    WamBr comp_wam_br_for_b_close_short() throws ToWarningLogException {
        List<BorderItem> o_br_close = bordersV2.getBorderTableByName("o_br_close")
                .get()
                .getBorderItemList();

        return calcByCloseTable(o_br_close, o_delta_plan, -pos_bo, -pos_ao);
    }

    private WamBr calcByOpenTable(List<BorderItem> br_open, BigDecimal delta_plan, int pos_bo_abs, int pos_ao_abs) throws ToWarningLogException {
        WamBr wamBr = new WamBr();

        int vol_fact = pos_ao_abs - pos_bo_abs;
        int vol_filled = 0;
        int bottomEdge = pos_bo_abs;

        for (int i = 0; i < br_open.size(); i++) {
            BigDecimal valueI = br_open.get(i).getValue();
            if (vol_filled >= vol_fact) {
                break;
            }
            if (br_open.get(i).getId() == 0) {
                continue;
            }

            int posShortLimit = usdToCont(br_open.get(i).getPosShortLimit());
            if (pos_bo_abs < posShortLimit) {
                int topEdge = pos_ao_abs < posShortLimit ? pos_ao_abs : posShortLimit;
                int amountStep = topEdge - bottomEdge;
                vol_filled += amountStep; // 1000 - 499 // 1499 - 1000
                bottomEdge = topEdge;

                BigDecimal amountPortion = BigDecimal.valueOf(amountStep)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);

//                checkByDeltaPlan(delta_plan, br_open.get(i));
                wamBr.add(amountPortion, valueI);
            }
        }
        return wamBr;
    }

    private WamBr calcByCloseTable(List<BorderItem> br_close, BigDecimal delta_plan, int pos_bo_abs, int pos_ao_abs) throws ToWarningLogException {
        WamBr wamBr = new WamBr();
        // 25 - 0
        // 25 - 1000
        // 20 - 2000
        int vol_fact = pos_bo_abs - pos_ao_abs;
        int vol_filled = 0;
        int bottomEdge = pos_ao_abs;

        for (int i = 0; i < br_close.size(); i++) {

            if (vol_filled >= vol_fact) {
                break;
            }
            if (br_close.get(i).getId() == 0) {
                continue;
            }

            BigDecimal valueI = br_close.get(i).getValue();

            int nextIndex = i + 1;
            BorderItem nextItem = br_close.get(i); // currItem
            if (nextIndex != br_close.size()) {
                nextItem = br_close.get(nextIndex);
                while (nextItem.getId() == 0 && nextIndex < br_close.size() - 1) {
                    nextIndex++;
                    nextItem = br_close.get(nextIndex);
                }
            }

            int posShortLimit = usdToCont(nextItem.getPosShortLimit());
            if (nextIndex == br_close.size()
                    || nextItem.getId() == 0
                    || pos_ao_abs < posShortLimit) { // it's last or less next

                int topEdge = (nextIndex == br_close.size()
                        || nextItem.getId() == 0
                        || pos_bo_abs < posShortLimit) // the last or the next
                        ? pos_bo_abs
                        : posShortLimit;

                int amountStep = topEdge - bottomEdge;
                vol_filled += amountStep;
                bottomEdge = topEdge;

                BigDecimal amountPortion = BigDecimal.valueOf(amountStep)
                        .divide(BigDecimal.valueOf(vol_fact), 16, BigDecimal.ROUND_HALF_UP)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);

//                checkByDeltaPlan(delta_plan, br_close.get(i));
                wamBr.add(amountPortion, valueI);
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
            throw new ToWarningLogException(String.format("Error: wam_br=0! pos_bo=%s, pos_ao=%s, pos_mode=%s, "
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
