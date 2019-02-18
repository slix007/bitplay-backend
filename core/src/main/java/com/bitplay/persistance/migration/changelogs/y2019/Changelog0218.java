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
public class Changelog0218 {

    @ChangeSet(order = "2019-02-18", id = "2019-02-18: BitmexChangeOnSo change", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        {
            Query query = new Query();
            Update update = new Update();
            update.rename("bitmexChangeOnSo.auto", "bitmexChangeOnSo.toTaker");
            update.set("bitmexChangeOnSo.toConBo", false);
            mongoTemplate.updateMulti(query, update, "settingsCollection");
        }
        {
            Query query = new Query();
            Update update = new Update();
            update.rename("settings.bitmexChangeOnSo.auto", "settings.bitmexChangeOnSo.toTaker");
            update.set("settings.bitmexChangeOnSo.toConBo", false);
            mongoTemplate.updateMulti(query, update, "settingsPresetCollection");
        }
    }


}
