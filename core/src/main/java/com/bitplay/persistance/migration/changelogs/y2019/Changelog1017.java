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
public class Changelog1017 {

    @ChangeSet(order = "2019-10-17", id = "2019-10-17: Add abortedSignalUnstartedVert", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("abortedSignalUnstartedVert1", 0);
        update.set("abortedSignalUnstartedVert2", 0);
        mongoTemplate.updateMulti(query, update, CumParams.class);
    }
}
