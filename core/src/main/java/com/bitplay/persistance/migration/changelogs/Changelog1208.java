package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.correction.Adj;
import com.bitplay.persistance.domain.correction.Corr;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1208 {

    @ChangeSet(order = "001", id = "2018-12-08: corr/adj totalCount", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final CorrParams corrParams = mongoTemplate.findById(1L, CorrParams.class);
        corrParams.setCorr(Corr.createDefault());
        corrParams.setAdj(Adj.createDefault());
        mongoTemplate.save(corrParams);
    }

}
