package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0418 {

    @ChangeSet(order = "001", id = "add avg_delta into BorderParams1", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final List<BorderParams> all = mongoTemplate.findAll(BorderParams.class);
        for (BorderParams borderParams : all) {
            borderParams.setBorderDelta(BorderDelta.createDefault());
            mongoTemplate.save(borderParams);
        }
    }

    @ChangeSet(order = "003", id = "2018-04-27:Okex placing attempts", author = "SergeiShurmin")
    public void change03(MongoTemplate mongoTemplate) {
        final List<Settings> all = mongoTemplate.findAll(Settings.class);
        for (Settings settings : all) {
            settings.getOkexSysOverloadArgs().setPlaceAttempts(2);
            mongoTemplate.save(settings);
        }
    }

}
