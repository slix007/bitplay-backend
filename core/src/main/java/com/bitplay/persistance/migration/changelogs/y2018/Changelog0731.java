package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.borders.BorderParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0731 {

    @ChangeSet(order = "2018-07-30-1", id = "2018-07-30:SMA Off", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final BorderParams borderParams = mongoTemplate.findById(1L, BorderParams.class);
        borderParams.getBorderDelta().setDeltaSmaCalcOn(true);
        mongoTemplate.save(borderParams);
    }

}
