package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.MarketSettings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0721 {

    @ChangeSet(order = "2019-07-21", id = "2019-07-21: Add marketSettings in DB", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final MarketSettings btm = new MarketSettings();
        final MarketSettings ok = new MarketSettings();
        btm.setMarketId(1);
        btm.setMarketName(BitmexService.NAME);
        ok.setMarketId(2);
        ok.setMarketName(OkCoinService.NAME);
        mongoTemplate.save(btm);
        mongoTemplate.save(ok);
    }
}
