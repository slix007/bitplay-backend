package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0711 {

    @ChangeSet(order = "001", id = "2018-07-11:Signal delay", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final List<Settings> all = mongoTemplate.findAll(Settings.class);
        for (Settings settings : all) {
            settings.setSignalDelayMs(1000);
            mongoTemplate.save(settings);
        }
    }

    @ChangeSet(order = "002", id = "2018-07-11:Cold storage btc", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final List<Settings> all = mongoTemplate.findAll(Settings.class);
        for (Settings settings : all) {
            settings.setColdStorageBtc(BigDecimal.ZERO);
            mongoTemplate.save(settings);
        }
    }

}
