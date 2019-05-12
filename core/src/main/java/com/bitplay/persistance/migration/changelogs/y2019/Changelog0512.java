package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0512 {

    @ChangeSet(order = "2019-05-12", id = "2019-05-12:overloadTimeMs", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.getBitmexSysOverloadArgs().setOverloadTimeMs(60000);
        mongoTemplate.save(settings);
        Query query = new Query();
        Update update = new Update();
        update.unset("bitmexSysOverloadArgs.overloadTimeSec");
        update.unset("okexSysOverloadArgs.overloadTimeSec");
        mongoTemplate.updateMulti(query, update, "settingsCollection");
    }

}
