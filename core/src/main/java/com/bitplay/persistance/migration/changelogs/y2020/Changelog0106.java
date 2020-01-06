package com.bitplay.persistance.migration.changelogs.y2020;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0106 {

    @ChangeSet(order = "2020-01-06", id = "2020-01-06: Add dql killpos", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("dql.btmDqlKillPos", BigDecimal.ZERO);
        update.set("dql.okexDqlKillPos", BigDecimal.ZERO);
        mongoTemplate.updateMulti(query, update, "settingsCollection");
    }

}
