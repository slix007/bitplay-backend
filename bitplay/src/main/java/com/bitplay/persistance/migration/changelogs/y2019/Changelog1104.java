package com.bitplay.persistance.migration.changelogs.y2019;

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
public class Changelog1104 {

    @ChangeSet(order = "2019-11-04", id = "2019-11-04: recovery nt_usd maxBlockUsd", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("recoveryNtUsd.maxBlockUsd", 0);
        mongoTemplate.updateMulti(query, update, CorrParams.class);
    }
}
