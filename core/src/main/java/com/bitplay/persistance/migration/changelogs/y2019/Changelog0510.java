package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.OkexPostOnlyArgs;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0510 {

    @ChangeSet(order = "2019-05-10", id = "2019-05-10:add okex postOnly", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setOkexPostOnlyArgs(OkexPostOnlyArgs.defaults());
        mongoTemplate.save(settings);
    }

}
