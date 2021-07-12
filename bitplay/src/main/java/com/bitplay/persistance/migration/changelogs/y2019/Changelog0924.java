package com.bitplay.persistance.migration.changelogs.y2019;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0924 {

    @ChangeSet(order = "2019-09-24", id = "2019-09-24: Okex portions.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("conBoPortions.minNtUsdToStartOkex", BigDecimal.ZERO);
        update.set("conBoPortions.maxPortionUsdOkex", BigDecimal.ZERO);
        mongoTemplate.updateMulti(query, update, "settingsCollection");
    }
}
