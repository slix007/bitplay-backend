package com.bitplay.arbitrage;

import com.bitplay.arbitrage.exceptions.ToWarningLogException;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV2;
import java.util.List;

class BordersTableValidator {

    void doCheck(BordersV2 bordersV2) throws ToWarningLogException {
        List<BorderTable> borderTableList = bordersV2.getBorderTableList();
        for (BorderTable borderTable : borderTableList) {
//            case "b_br_open":
//            case "b_br_close":
//            case "o_br_open":
//            case "o_br_close":
            if (borderTable.getBorderName().endsWith("open")) {
                check_br_table(borderTable, true);
            } else {
                check_br_table(borderTable, false);
            }

        }
    }

    // functions
    private int find_last_active_row(BorderTable borderTable) {        // функция нахождения последней строки в class table[]
        List<BorderItem> table = borderTable.getBorderItemList();
        return find_last_active_row(table);
    }
    // functions
    static int find_last_active_row(List<BorderItem> table) {        // функция нахождения последней строки в class table[]
        int table_cnt = table.size();
        for (int i = 0; i < table_cnt; i++) {        // i = номер строки в class table[], table_cnt - количество строк в class table[]
            if (table.get(i).getId() == 0) {
                return i;
            }
        }
        return table.size() - 1;
    }



    private int check_br_table(BorderTable borderTable, boolean isOpenTable) throws ToWarningLogException {
        int err_cnt = 0;
        StringBuilder err_sb = new StringBuilder();

        try {
            List<BorderItem> borderItemList = borderTable.getBorderItemList();

            int k = find_last_active_row(borderTable);

            for (int i = 0; i < k - 1; i++) {
                BorderItem item = borderItemList.get(i);
                BorderItem itemNext = borderItemList.get(i + 1);
                if (itemNext != null && itemNext.getValue() != null) {
                    if ((!isOpenTable && item.getValue().compareTo(itemNext.getValue()) < 0)
                            || (isOpenTable && item.getValue().compareTo(itemNext.getValue()) > 0)) {
                        err_cnt++;
                        err_sb.append(String.format("Error check_br_table: %s.value order. \n", borderTable.getBorderName()));
                    }
                    if (item.getPosLongLimit() > itemNext.getPosLongLimit() || item.getPosShortLimit() > itemNext.getPosShortLimit()) {
                        err_cnt++;
                        err_sb.append(String.format("Error check_br_table: %s.pos_lim order. \n", borderTable.getBorderName()));
                    }
                }
            }
        } catch (Exception e) {
            throw new ToWarningLogException(String.format("BordersTableValidation error: %s. \n%s", e.toString(), err_sb.toString()));
        }

        if (err_cnt > 0) {
            throw new ToWarningLogException(String.format("Errors: %s.\n%s", err_cnt, err_sb.toString()));
        }

        return err_cnt;
    }

}
