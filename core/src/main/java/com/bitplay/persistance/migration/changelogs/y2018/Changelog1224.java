package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.settings.BitmexChangeOnSo;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1224 {

    @ChangeSet(order = "001", id = "2018-12-24: add BitmexChangeOnSo", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);

        final BitmexChangeOnSo bitmexChangeOnSo = new BitmexChangeOnSo();
        bitmexChangeOnSo.setAuto(false);
        bitmexChangeOnSo.setCountToActivate(50);
        bitmexChangeOnSo.setDurationSec(60);
        settings.setBitmexChangeOnSo(bitmexChangeOnSo);

        mongoTemplate.save(settings);
    }

}
