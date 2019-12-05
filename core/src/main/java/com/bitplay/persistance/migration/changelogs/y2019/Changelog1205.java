package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.ConBoPortions;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SettingsVolatileMode;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1205 {

    @ChangeSet(order = "2019-12-05", id = "2019-12-05: conBoPorstions volatile settings ", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        final SettingsVolatileMode settingsVolatileMode = settings.getSettingsVolatileMode();
        settingsVolatileMode.setConBoPortions(ConBoPortions.createDefault());
        mongoTemplate.save(settings);
    }
}
