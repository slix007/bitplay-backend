package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.CumParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0805 {

    @ChangeSet(order = "2019-08-05", id = "2019-08-05: Reset CumParams 6", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final CumParams c10 = mongoTemplate.findById(10L, CumParams.class);
        c10.setDefaults();
        mongoTemplate.save(c10);
        final CumParams c11 = mongoTemplate.findById(11L, CumParams.class);
        c11.setDefaults();
        mongoTemplate.save(c11);

        final CumParams c12 = mongoTemplate.findById(12L, CumParams.class);
        c12.setDefaults();
        mongoTemplate.save(c12);
        final CumParams c13 = mongoTemplate.findById(13L, CumParams.class);
        c13.setDefaults();
        mongoTemplate.save(c13);

        final CumParams c14 = mongoTemplate.findById(14L, CumParams.class);
        c14.setDefaults();
        mongoTemplate.save(c14);
        final CumParams c15 = mongoTemplate.findById(15L, CumParams.class);
        c15.setDefaults();
        mongoTemplate.save(c15);
    }
}
