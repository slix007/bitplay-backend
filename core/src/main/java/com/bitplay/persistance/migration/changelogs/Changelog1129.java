package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.LastPriceDeviation;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1129 {

    @ChangeSet(order = "001", id = "2018-11-29: lastPriceDeviation", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        LastPriceDeviation lastPriceDeviation = new LastPriceDeviation();
        lastPriceDeviation.setId(4L);
        lastPriceDeviation.setPercentage(BigDecimal.valueOf(10));
        mongoTemplate.save(lastPriceDeviation);
    }

}
