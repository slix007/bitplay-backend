package com.bitplay.persistance.migration.changelogs.y2021;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.BtmAvgPriceUpdateSettings;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog
public class Changelog0619 {

    @ChangeSet(order = "2021-06-19", id = "2021-06-19: BtmAvgPriceUpdateSettings.", author = "Sergey Shurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setBtmAvgPriceUpdateSettings(BtmAvgPriceUpdateSettings.createDefault());
        mongoTemplate.save(settings);
        SettingsRepositoryService.setInvalidated();
    }

}
