package com.bitplay.persistance.migration.changelogs.y2020;

import com.bitplay.persistance.domain.settings.OkexFtpd;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0116 {

    @ChangeSet(order = "2020-01-16", id = "2020-01-16: okex ftpd percent", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        final BigDecimal okexFakeTakerDev = settings.getOkexFtpd() != null
                ? settings.getOkexFakeTakerDev()
                : BigDecimal.ONE;

        Query query = new Query();
        Update update = new Update();
        update.set("okexFtpd.okexFtpdPts", okexFakeTakerDev);
        update.set("okexFtpd.okexFtpdBod", BigDecimal.ZERO);
        update.set("okexFtpd.okexFtpdType", OkexFtpd.OkexFtpdType.PTS);
        mongoTemplate.updateMulti(query, update, "settingsCollection");
    }

}
