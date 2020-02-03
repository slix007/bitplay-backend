package com.bitplay.persistance.migration.changelogs.y2020;

import com.bitplay.persistance.domain.correction.CorrParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0131 {

    @ChangeSet(order = "2020-01-31", id = "2020-01-31: killpos timer", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("killpos.preliqBlockUsd", 0);
        update.set("killpos.currErrorCount", 0);
        update.set("killpos.maxErrorCount", 0);
        update.set("killpos.totalCount", 0);
        update.set("killpos.succeedCount", 0);
        update.set("killpos.failedCount", 0);
        update.set("killpos.maxTotalCount", 0);
        mongoTemplate.updateMulti(query, update, CorrParams.class);
    }


}
