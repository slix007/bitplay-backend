package com.bitplay.persistance.migration.changelogs.y2020;

import com.bitplay.persistance.domain.settings.AllPostOnlyArgs;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0304 {

    @ChangeSet(order = "2020-03-03", id = "2020-03-03: usdQuoteType left-right..", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setAllPostOnlyArgs(AllPostOnlyArgs.defaults());
        mongoTemplate.save(settings);
    }


}
