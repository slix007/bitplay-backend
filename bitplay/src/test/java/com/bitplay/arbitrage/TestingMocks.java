package com.bitplay.arbitrage;

import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV2;

/**
 * Created by Sergey Shurmin on 1/13/18.
 */
public class TestingMocks {

    static BorderParams toUsd(BorderParams borderParams) {
        return toUsd(borderParams, false);
    }

    @SuppressWarnings("Duplicates")
    static BorderParams toUsd(BorderParams borderParams, boolean isEth) {
        BordersV2 bordersV2 = borderParams.getBordersV2();
        PosMode posMode = borderParams.getPosMode();

        for (BorderTable borderTable : bordersV2.getBorderTableList()) {
            for (BorderItem borderItem : borderTable.getBorderItemList()) {
                if (posMode == PosMode.RIGHT_MODE) {
                    int mlt = !isEth
                            ? 100 // set_bu: 100 USD = 1 Okex_cont.
                            : 10; // set_eu: 10 USD = 1 Okex_cont.
                    borderItem.setPosShortLimit(mlt * borderItem.getPosShortLimit());
                    borderItem.setPosLongLimit(mlt * borderItem.getPosLongLimit());
                } else if (posMode == PosMode.LEFT_MODE) {
                    double cm = 10; // just a guess.
                    double mlt = !isEth
                            ? 1 // set_bu: 1 USD = 1 Bitmex_cont.
                            : cm / 10; // set_eu: 10 / CM USD = 1 Bitmex_cont.

                    borderItem.setPosShortLimit((int) mlt * borderItem.getPosShortLimit());
                    borderItem.setPosLongLimit((int) mlt * borderItem.getPosLongLimit());
                }
            }
        }
        return borderParams;
    }


}
