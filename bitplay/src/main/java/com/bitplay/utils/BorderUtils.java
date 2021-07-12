package com.bitplay.utils;

import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV2;

public class BorderUtils {

    public static BorderParams withPlm(BorderParams borderParams) {
        int plm = borderParams.getBordersV2().getPlm().intValue();
        BordersV2 bordersV2 = borderParams.getBordersV2();
        for (BorderTable borderTable : bordersV2.getBorderTableList()) {
            for (BorderItem borderItem : borderTable.getBorderItemList()) {
                borderItem.setPosShortLimit(borderItem.getPosShortLimit() / plm);
                borderItem.setPosLongLimit(borderItem.getPosLongLimit() / plm);
            }
        }
        return borderParams;
    }

}