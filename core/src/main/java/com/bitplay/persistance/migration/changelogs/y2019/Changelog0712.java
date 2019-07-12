package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.CumParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0712 {

    @ChangeSet(order = "2019-07-12", id = "2019-07-12: Add pre-signal-reCheck unstartedVert.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("obRecheckUnstartedVert1", 0);
        update.set("obRecheckUnstartedVert2", 0);
        mongoTemplate.updateMulti(query, update, CumParams.class);
    }
}
