package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0320 {

    @ChangeSet(order = "2019-03-20", id = "2019-03-20: VOLATILE mode arbScheme", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        final ArbScheme arbScheme = settings.getArbScheme();
        settings.getSettingsVolatileMode().setArbScheme(arbScheme);
        mongoTemplate.save(settings);
    }

}
