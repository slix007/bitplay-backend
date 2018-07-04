package com.bitplay.persistance.migration.changelogs;

import com.bitplay.api.controller.BordersEndpoint;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0704 {

    @ChangeSet(order = "001", id = "2018-07-04:Create border param if null", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final List<BorderParams> all = mongoTemplate.findAll(BorderParams.class);

//        BorderParams one = borderParamsRepository.findOne(1L);
        if (all.size() == 0) {
            BorderParams one = BordersEndpoint.createDefaultBorders();
            mongoTemplate.save(one);
        }
    }

    @ChangeSet(order = "002", id = "2018-07-04:Create corr param if null", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final List<CorrParams> allCorr = mongoTemplate.findAll(CorrParams.class);
        if (allCorr.size() == 0) {
            CorrParams aDefault = CorrParams.createDefault();
            mongoTemplate.save(aDefault);
        }
    }

}
