package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0815 {

    @ChangeSet(order = "001", id = "2018-08-16:Okcoin future contract type", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        if (settings != null) {
            settings.setOkexContractType(OkexContractType.BTC_NextWeek);
            mongoTemplate.save(settings);
        }
    }

    @ChangeSet(order = "002", id = "2018-08-19:Bitmex future contract type", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        if (settings != null) {
            settings.setBitmexContractType(BitmexContractType.XBTUSD);
            mongoTemplate.save(settings);
        }
    }

}
