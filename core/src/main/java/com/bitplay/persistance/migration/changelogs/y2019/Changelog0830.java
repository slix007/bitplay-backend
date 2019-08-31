package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.OkexSettlement;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0830 {

    @ChangeSet(order = "2019-08-30", id = "2019-08-30: Okex settlement", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setOkexSettlement(OkexSettlement.createDefault());
        mongoTemplate.save(settings);
    }
}
