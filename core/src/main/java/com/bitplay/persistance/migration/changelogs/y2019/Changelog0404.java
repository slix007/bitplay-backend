package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.CumTimeType;
import com.bitplay.persistance.domain.CumType;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0404 {

    @ChangeSet(order = "2019-04-04", id = "2019-04-04:CumParams curr and volatile", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        CumParams copy = new CumParams();
        copy.setDefaults();

        copy.setId(10L);
        mongoTemplate.save(copy);
        copy.setId(11L);
        mongoTemplate.save(copy);
        copy.setId(12L);
        mongoTemplate.save(copy);
        copy.setId(13L);
        mongoTemplate.save(copy);
        copy.setId(14L);
        mongoTemplate.save(copy);
        copy.setId(15L);
        mongoTemplate.save(copy);
    }

    @ChangeSet(order = "2019-04-06", id = "2019-04-06:CumParams curr and volatile", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final CumParams c10 = mongoTemplate.findById(10L, CumParams.class);
        c10.setCumType(CumType.TOTAL);
        c10.setCumTimeType(CumTimeType.COMMON);
        mongoTemplate.save(c10);
        final CumParams c11 = mongoTemplate.findById(11L, CumParams.class);
        c11.setCumType(CumType.TOTAL);
        c11.setCumTimeType(CumTimeType.EXTENDED);
        mongoTemplate.save(c11);

        final CumParams c12 = mongoTemplate.findById(12L, CumParams.class);
        c12.setCumType(CumType.CURRENT);
        c12.setCumTimeType(CumTimeType.COMMON);
        mongoTemplate.save(c12);
        final CumParams c13 = mongoTemplate.findById(13L, CumParams.class);
        c13.setCumType(CumType.CURRENT);
        c13.setCumTimeType(CumTimeType.EXTENDED);
        mongoTemplate.save(c13);

        final CumParams c14 = mongoTemplate.findById(14L, CumParams.class);
        c14.setCumType(CumType.VOLATILE);
        c14.setCumTimeType(CumTimeType.COMMON);
        mongoTemplate.save(c14);
        final CumParams c15 = mongoTemplate.findById(15L, CumParams.class);
        c15.setCumType(CumType.VOLATILE);
        c15.setCumTimeType(CumTimeType.EXTENDED);
        mongoTemplate.save(c15);
    }


}
