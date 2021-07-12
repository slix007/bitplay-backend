package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.correction.CorrParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0727 {

    @ChangeSet(order = "2018-07-27-1", id = "2018-07-27:Preliq total", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final CorrParams corrParams = mongoTemplate.findById(1L, CorrParams.class);
        corrParams.getPreliq().setTotalCount(corrParams.getPreliq().getSucceedCount() + corrParams.getPreliq().getFailedCount());
        mongoTemplate.save(corrParams);
    }

}
