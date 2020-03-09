package com.bitplay.persistance.migration.changelogs.y2020;

import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.FeeSettings;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0306 {

    @ChangeSet(order = "2020-03-06", id = "2020-03-06: pos mode left-right.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("posMode", "RIGHT_MODE");
        update.set("bordersV2.baseLvlType", "RIGHT_OPEN");
        mongoTemplate.updateMulti(query, update, BorderParams.class);
    }

    @ChangeSet(order = "2020-03-06", id = "2020-03-06: fee left-right.", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setFeeSettings(FeeSettings.createDefault());
        mongoTemplate.save(settings);
    }

    @ChangeSet(order = "2020-03-06", id = "2020-03-06: dql left-right.", author = "SergeiShurmin")
    public void change03(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
//        update.set("dql.bMrLiq", BigDecimal.valueOf(75));
//        update.set("dql.oMrLiq", BigDecimal.valueOf(20));
        update.set("dql.leftDqlOpenMin", BigDecimal.valueOf(300));
        update.set("dql.rightDqlOpenMin", BigDecimal.valueOf(350));
        update.set("dql.leftDqlCloseMin", BigDecimal.valueOf(150));
        update.set("dql.rightDqlCloseMin", BigDecimal.valueOf(150));
        update.set("dql.leftDqlKillPos", BigDecimal.ZERO);
        update.set("dql.leftDqlKillPos", BigDecimal.ZERO);
        mongoTemplate.updateMulti(query, update, "settingsCollection");
    }

    @ChangeSet(order = "2020-03-09", id = "2020-03-09: arbScheme rename.", author = "SergeiShurmin")
    public void change04(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        ArbScheme old = settings.getArbScheme();
        if (old == ArbScheme.SIM) {
            settings.setArbScheme(ArbScheme.L_with_R);
        } else if (old == ArbScheme.CON_B_O) {
            settings.setArbScheme(ArbScheme.R_wait_L);
        } else {
            settings.setArbScheme(ArbScheme.R_wait_L_portions);
        }

        ArbScheme vOld = settings.getSettingsVolatileMode().getArbScheme();
        if (vOld == ArbScheme.SIM) {
            settings.getSettingsVolatileMode().setArbScheme(ArbScheme.L_with_R);
        } else if (vOld == ArbScheme.CON_B_O) {
            settings.getSettingsVolatileMode().setArbScheme(ArbScheme.R_wait_L);
        } else {
            settings.getSettingsVolatileMode().setArbScheme(ArbScheme.R_wait_L_portions);
        }
        mongoTemplate.save(settings);
    }

}
