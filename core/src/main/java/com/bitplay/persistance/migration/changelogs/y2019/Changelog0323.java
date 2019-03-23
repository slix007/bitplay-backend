package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0323 {

    @ChangeSet(order = "2019-03-23: 002", id = "2019-03-23: remove OkexLimitPrice (always 1)", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        // remove old
        Query query = new Query(Criteria.where("_id").is(1L));
        Update update = new Update();
        update.unset("limits.okexLimitPrice");
        mongoTemplate.updateMulti(query, update, Settings.class);
    }

}
