package com.bitplay.persistance.migration.changelogs.y2020;

import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ChangeLog
public class Changelog0901 {

    @ChangeSet(order = "2020-09-01", id = "2020-09-01: acceptable OB timestamps.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("settingsTimestamps.L_Acceptable_Get_OB_Delay_ms", "0");
        update.set("settingsTimestamps.R_Acceptable_Get_OB_Delay_ms", "0");
        update.set("settingsTimestamps.L_Acceptable_OB_Timestamp_Diff_ms", "0");
        update.set("settingsTimestamps.R_Acceptable_OB_Timestamp_Diff_ms", "0");
        mongoTemplate.updateMulti(query, update, Settings.class);
    }

}
