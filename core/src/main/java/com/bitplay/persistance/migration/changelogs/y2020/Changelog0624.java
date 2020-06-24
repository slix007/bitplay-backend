package com.bitplay.persistance.migration.changelogs.y2020;

import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ChangeLog
public class Changelog0624 {

    @ChangeSet(order = "2020-06-24", id = "2020-06-24: implied.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("implied.volUsd", "0");
        update.set("implied.usdQuIni", "0");
        update.set("implied.sebestIniUsd", "0");
        mongoTemplate.updateMulti(query, update, Settings.class);
    }

}
