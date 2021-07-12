package com.bitplay.persistance.migration.changelogs.y2019;

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
public class Changelog1011 {

    @ChangeSet(order = "2019-10-11", id = "2019-10-11: abortSignalPts.f1", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("abortSignal.abortSignalPtsEnabled", false);
        update.set("abortSignal.abortSignalPts", BigDecimal.ZERO);
        mongoTemplate.updateMulti(query, update, "settingsCollection");
    }
}
