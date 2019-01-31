package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.UsdQuoteType;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0711 {

    @ChangeSet(order = "001", id = "2018-07-11:Signal delay", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final List<Settings> all = mongoTemplate.findAll(Settings.class);
        for (Settings settings : all) {
            settings.setSignalDelayMs(1000);
            mongoTemplate.save(settings);
        }
    }

    @ChangeSet(order = "002", id = "2018-07-11:Cold storage btc", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final List<Settings> all = mongoTemplate.findAll(Settings.class);
        for (Settings settings : all) {
            settings.setColdStorageBtc(BigDecimal.ZERO);
            mongoTemplate.save(settings);
        }
    }

    @ChangeSet(order = "003", id = "2018-07-14:Usd quote type", author = "SergeiShurmin")
    public void change03(MongoTemplate mongoTemplate) {
        final List<Settings> all = mongoTemplate.findAll(Settings.class);
        for (Settings settings : all) {
            settings.setUsdQuoteType(UsdQuoteType.AVG);
            mongoTemplate.save(settings);
        }
    }

    @ChangeSet(order = "004", id = "2018-08-31:e best min", author = "SergeiShurmin")
    public void change04(MongoTemplate mongoTemplate) {
        final List<Settings> all = mongoTemplate.findAll(Settings.class);
        for (Settings settings : all) {
            settings.setEBestMin(0);
            mongoTemplate.save(settings);
        }
    }

}
