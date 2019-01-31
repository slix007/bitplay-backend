package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.SignalTimeParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0810 {

    @ChangeSet(order = "2018-08-10-1", id = "2018-08-10:SignalTime", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        SignalTimeParams deltaParams = SignalTimeParams.createDefault();
        deltaParams.setId(3L);
        deltaParams.setName("SignalTimeParams");
        mongoTemplate.save(deltaParams);
    }

}
