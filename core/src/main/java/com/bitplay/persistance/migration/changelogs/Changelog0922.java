package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.correction.Adj;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.PosAdjustment;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0922 {

    @ChangeSet(order = "001", id = "2018-09-22:posAdjustment params", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        if (settings != null) {
            settings.setPosAdjustment(PosAdjustment.createDefault());
            mongoTemplate.save(settings);
        }
    }

    @ChangeSet(order = "002", id = "2018-09-24:posAdjustment counters", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        CorrParams corrParams = mongoTemplate.findById(1L, CorrParams.class);
        if (corrParams != null) {
            corrParams.setAdj(Adj.createDefault());
            mongoTemplate.save(corrParams);
        } else {
            corrParams = CorrParams.createDefault();
            mongoTemplate.save(corrParams);
        }
    }
}
