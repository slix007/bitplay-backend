package com.bitplay.persistance.migration.changelogs.y2020;

import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0219 {

    @ChangeSet(order = "2020-02-19", id = "2020-02-19: change contractMode(okex-okex)", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.unset("contractMode");
        mongoTemplate.updateMulti(query, update, Settings.class);

        Query query2 = new Query();
        Update update2 = new Update();
        update2.set("contractMode.left", BitmexContractType.XBTUSD);
        update2.set("contractMode.right", OkexContractType.BTC_ThisWeek);
        mongoTemplate.updateMulti(query2, update2, Settings.class);
    }


}
