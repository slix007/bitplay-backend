package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1120 {

    @ChangeSet(order = "001", id = "2018-11-20: placing blocks in usd", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        PlacingBlocks placingBlocks = settings.getPlacingBlocks();
        placingBlocks.setFixedBlockUsd(BigDecimal.valueOf(100));
        placingBlocks.setDynMaxBlockUsd(BigDecimal.valueOf(100));
        mongoTemplate.save(settings);
    }
}
