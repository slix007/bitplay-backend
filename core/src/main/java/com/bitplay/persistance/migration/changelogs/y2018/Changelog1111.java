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
public class Changelog1111 {

    @ChangeSet(order = "001", id = "2018-11-11: Okex fake taker deviation", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        if (settings != null) {
            settings.setOkexFakeTakerDev(BigDecimal.ONE);
            mongoTemplate.save(settings);
        }
    }
}
