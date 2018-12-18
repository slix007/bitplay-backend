package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.LastPriceDeviation;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1129 {

    @ChangeSet(order = "001", id = "2018-11-29: lastPriceDeviation", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        LastPriceDeviation lastPriceDeviation = new LastPriceDeviation();
        lastPriceDeviation.setId(4L);
//        lastPriceDeviation.setPercentage(BigDecimal.valueOf(10)); // removed
        mongoTemplate.save(lastPriceDeviation);
    }

    @ChangeSet(order = "002", id = "2018-12-11: lastPriceDeviation timer", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final LastPriceDeviation lastPriceDeviation = mongoTemplate.findById(4L, LastPriceDeviation.class);
        lastPriceDeviation.setDelaySec(3600);
        mongoTemplate.save(lastPriceDeviation);
    }

    @ChangeSet(order = "003", id = "2018-12-11: lastPriceDeviation maxDevUsd", author = "SergeiShurmin")
    public void change03(MongoTemplate mongoTemplate) {
        // remove old
        Query query = new Query(Criteria.where("_id").is(4L));
        Update update = new Update();
        update.unset("percentage");
        update.unset("bitmexExtra");
        mongoTemplate.updateMulti(query, update, LastPriceDeviation.class);

        final LastPriceDeviation lastPriceDeviation = mongoTemplate.findById(4L, LastPriceDeviation.class);
        lastPriceDeviation.setMaxDevUsd(BigDecimal.valueOf(10));
        mongoTemplate.save(lastPriceDeviation);
    }

    @ChangeSet(order = "004", id = "2018-12-11: lastPriceDeviation fix missed", author = "SergeiShurmin")
    public void change04(MongoTemplate mongoTemplate) {
        final LastPriceDeviation lastPriceDeviation = mongoTemplate.findById(4L, LastPriceDeviation.class);
        if (lastPriceDeviation.getDelaySec() == null) {
            lastPriceDeviation.setDelaySec(3600);
        }
        if (lastPriceDeviation.getMaxDevUsd() == null) {
            lastPriceDeviation.setMaxDevUsd(BigDecimal.valueOf(10));
        }
        mongoTemplate.save(lastPriceDeviation);
    }
}
