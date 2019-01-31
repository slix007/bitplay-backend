package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV2;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1120 {

    @ChangeSet(order = "001", id = "2018-11-20: placing blocks in usd", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        PlacingBlocks placingBlocks = settings.getPlacingBlocks();
        placingBlocks.setFixedBlockUsd(BigDecimal.valueOf(100));
        placingBlocks.setDynMaxBlockUsd(BigDecimal.valueOf(100));
        mongoTemplate.save(settings);
    }

    @ChangeSet(order = "001", id = "2018-11-20: corr/preliq in usd", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final CorrParams corrParams = mongoTemplate.findById(1L, CorrParams.class);
        corrParams.getCorr().setMaxVolCorrUsd(1);
        corrParams.getPreliq().setPreliqBlockUsd(1);
        mongoTemplate.save(corrParams);
    }

    @ChangeSet(order = "003", id = "2018-11-20: borderParams", author = "SergeiShurmin")
    public void change03(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        boolean isEth = settings.getContractMode().isEth();

        final BorderParams borderParams = mongoTemplate.findById(1L, BorderParams.class);
        BordersV2 bordersV2 = borderParams.getBordersV2();
        PosMode posMode = borderParams.getPosMode();

        for (BorderTable borderTable : bordersV2.getBorderTableList()) {
            for (BorderItem borderItem : borderTable.getBorderItemList()) {
                if (posMode == PosMode.OK_MODE) {
                    int mlt = !isEth
                            ? 100 // set_bu: 100 USD = 1 Okex_cont.
                            : 10; // set_eu: 10 USD = 1 Okex_cont.
                    borderItem.setPosShortLimit(mlt * borderItem.getPosShortLimit());
                    borderItem.setPosLongLimit(mlt * borderItem.getPosLongLimit());
                } else if (posMode == PosMode.BTM_MODE) {
                    double cm = 10; // just a guess.
                    double mlt = !isEth
                            ? 1 // set_bu: 1 USD = 1 Bitmex_cont.
                            : cm / 10; // set_eu: 10 / CM USD = 1 Bitmex_cont.

                    borderItem.setPosShortLimit((int) mlt * borderItem.getPosShortLimit());
                    borderItem.setPosLongLimit((int) mlt * borderItem.getPosLongLimit());
                }
            }
        }

        mongoTemplate.save(borderParams);
    }

    @ChangeSet(order = "004", id = "2018-11-20: PLM", author = "SergeiShurmin")
    public void change04(MongoTemplate mongoTemplate) {
        final BorderParams borderParams = mongoTemplate.findById(1L, BorderParams.class);
        BordersV2 bordersV2 = borderParams.getBordersV2();
        bordersV2.setPlm(BigDecimal.ONE);
        mongoTemplate.save(borderParams);
    }

    @ChangeSet(order = "005", id = "2018-11-21: preliqDelaySec", author = "SergeiShurmin")
    public void change05(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        if (settings != null) {
            settings.getPosAdjustment().setPreliqDelaySec(10);
            mongoTemplate.save(settings);
        }
    }
}
