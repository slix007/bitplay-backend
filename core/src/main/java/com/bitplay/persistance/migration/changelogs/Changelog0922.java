package com.bitplay.persistance.migration.changelogs;

import com.bitplay.market.model.PlacingType;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0922 {

    @ChangeSet(order = "001", id = "2018-09-22:add posAdjustment params", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        if (settings != null && settings.getPlacingBlocks() != null) {
            settings.getPlacingBlocks().setPosAdjustment(BigDecimal.ZERO);
            settings.getPlacingBlocks().setPosAdjustmentPlacingType(PlacingType.TAKER);
            mongoTemplate.save(settings);
        }
    }

}
