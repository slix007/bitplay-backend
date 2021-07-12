package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0506 {

    @ChangeSet(order = "2019-05-06", id = "2019-05-06:add betweenAttemptsMs", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.getBitmexSysOverloadArgs().setBetweenAttemptsMs(500);
        mongoTemplate.save(settings);
    }

}
