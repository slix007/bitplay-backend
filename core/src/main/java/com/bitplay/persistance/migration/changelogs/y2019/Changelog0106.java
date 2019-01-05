package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.borders.BorderParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0106 {

    @ChangeSet(order = "001", id = "2019-01-06: max border", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final BorderParams borderParams = mongoTemplate.findById(1L, BorderParams.class);
        borderParams.setMaxBorder(BigDecimal.valueOf(9999));
        borderParams.setOnlyOpen(false);
        mongoTemplate.save(borderParams);
    }

}
