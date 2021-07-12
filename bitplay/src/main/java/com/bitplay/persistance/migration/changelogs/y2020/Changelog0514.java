package com.bitplay.persistance.migration.changelogs.y2020;

import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ChangeLog
public class Changelog0514 {

    @ChangeSet(order = "2020-05-14", id = "2020-05-14: bitmex contract type.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.set("bitmexContractTypes.btcUsdQuoter", "XBTM20");
        update.set("bitmexContractTypes.btcUsdBiQuoter", "XBTU20");
        update.set("bitmexContractTypes.ethUsdQuoter", "ETHM20");
        mongoTemplate.updateMulti(query, update, Settings.class);
    }

}
