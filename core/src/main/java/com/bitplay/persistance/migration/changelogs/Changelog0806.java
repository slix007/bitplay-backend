package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0806 {

        @ChangeSet(order = "2018-08-07-1", id = "2018-08-07:Delta min", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        DeltaParams deltaParams = DeltaParams.createDefault();
        deltaParams.setId(2L);
        deltaParams.setName("DeltaMin");
        mongoTemplate.save(deltaParams);
    }

    @ChangeSet(order = "2018-08-07-2", id = "2018-08-07-2:Delta min", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        BorderParams borderParams = mongoTemplate.findById(1L, BorderParams.class);
        borderParams.setDeltaMinFixPeriodSec(30);
        mongoTemplate.save(borderParams);
    }


}
