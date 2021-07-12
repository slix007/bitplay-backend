package com.bitplay.persistance.migration.changelogs.y2020;

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
public class Changelog0303 {

    @ChangeSet(order = "2020-03-03", id = "2020-03-03: usdQuoteType left-right", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("usdQuoteType", "LEFT");
        mongoTemplate.updateMulti(query, update, Settings.class);
    }


}
