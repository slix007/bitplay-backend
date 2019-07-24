package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.MarketSettings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0724 {

    @ChangeSet(order = "2019-07-24", id = "2019-07-24: Fix DB from scratch.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final LiqParams liqParams = new LiqParams();
        liqParams.setId(1L);
        liqParams.setMarketName(BitmexService.NAME);
        mongoTemplate.save(liqParams);
        final LiqParams ok = new LiqParams();
        ok.setId(2L);
        ok.setMarketName(OkCoinService.NAME);
        mongoTemplate.save(ok);
    }
}
