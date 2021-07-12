package com.bitplay.persistance.migration.changelogs.y2019;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0830 {

    @ChangeSet(order = "2019-08-30", id = "2019-08-30: Okex settlement", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("okexSettlement.active", false);
        update.set("okexSettlement.period", 5);
        mongoTemplate.updateMulti(query, update, "settingsCollection");
    }
}
