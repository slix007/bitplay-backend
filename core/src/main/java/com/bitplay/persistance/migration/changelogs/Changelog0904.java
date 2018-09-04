package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.settings.RestartSettings;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0904 {

    @ChangeSet(order = "001", id = "2018-09-04:Max bitmex reconnect", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        if (settings != null) {
            RestartSettings restartSettings = settings.getRestartSettings();
            if (restartSettings != null) {
                restartSettings.setMaxBitmexReconnects(10);
                mongoTemplate.save(settings);
            }
        }
    }

}
