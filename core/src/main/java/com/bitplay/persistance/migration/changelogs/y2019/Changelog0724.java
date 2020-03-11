package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.MarketSettings;
import com.bitplay.persistance.domain.SwapParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0724 {

    @ChangeSet(order = "2019-07-24", id = "2019-07-24: Fix DB from scratch.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final LiqParams liqParams = new LiqParams();
//        liqParams.setId(1L);
        liqParams.setMarketName(BitmexService.NAME);
        mongoTemplate.save(liqParams);
        final LiqParams ok = new LiqParams();
//        ok.setId(2L);
        ok.setMarketName(OkCoinService.NAME);
        mongoTemplate.save(ok);
    }

    @ChangeSet(order = "2019-07-25", id = "2019-07-25: Fix DB from scratch 5.", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        Query query = new Query(Criteria.where("marketName").is(BitmexService.NAME));
        final SwapParams one = mongoTemplate.findOne(query, SwapParams.class);
        if (one == null) {
            SwapParams swapParams = SwapParams.createDefault();
//            swapParams.setId(1L);
            swapParams.setMarketName(BitmexService.NAME);
            mongoTemplate.save(swapParams);
        }
    }
}
