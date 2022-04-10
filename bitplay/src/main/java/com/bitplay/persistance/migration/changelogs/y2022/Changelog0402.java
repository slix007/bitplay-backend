package com.bitplay.persistance.migration.changelogs.y2022;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.FundingSettings;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ChangeLog
public class Changelog0402 {

    @ChangeSet(order = "2022-04-02", id = "2022-04-02: funding result timers.", author = "Sergey Shurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setFundingSettings(FundingSettings.createDefault());
        mongoTemplate.save(settings);
        SettingsRepositoryService.setInvalidated();
    }

    @ChangeSet(order = "2022-04-10", id = "2022-04-10: funding result enabled.", author = "Sergey Shurmin")
    public void change03(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("fundingSettings.fundingResultEnabled", false);
        mongoTemplate.updateMulti(query, update, "settingsCollection");
        SettingsRepositoryService.setInvalidated();
    }
}
