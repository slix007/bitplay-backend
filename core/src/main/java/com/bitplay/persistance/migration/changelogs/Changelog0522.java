package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.RestartSettings;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0522 {

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    @ChangeSet(order = "001", id = "2018-05-22:Restart settings2", author = "SergeiShurmin")
    public void change1(MongoTemplate mongoTemplate) {
        final List<Settings> all = mongoTemplate.findAll(Settings.class);
        for (Settings settings : all) {
            settings.setRestartSettings(RestartSettings.createDefaults());
            mongoTemplate.save(settings);
        }
    }

}
