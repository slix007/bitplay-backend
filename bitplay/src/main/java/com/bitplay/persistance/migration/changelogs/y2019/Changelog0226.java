package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0226 {

    @ChangeSet(order = "2019-02-26", id = "2019-02-26:Cold storage eth", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setColdStorageEth(BigDecimal.ZERO);
        mongoTemplate.save(settings);
    }

}
