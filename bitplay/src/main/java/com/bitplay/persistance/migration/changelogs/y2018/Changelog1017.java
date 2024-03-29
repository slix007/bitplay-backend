package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1017 {

    @ChangeSet(order = "002", id = "2018-10-17:Add hedge amount to settings..", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        Settings settings = mongoTemplate.findById(1L, Settings.class);
        if (settings == null) {
            settings = Settings.createDefault();
            settings.setId(1L);
        }
        settings.setHedgeBtc(BigDecimal.ZERO);
        settings.setHedgeEth(BigDecimal.ZERO);
        settings.setHedgeAuto(false);
        mongoTemplate.save(settings);
    }
}
