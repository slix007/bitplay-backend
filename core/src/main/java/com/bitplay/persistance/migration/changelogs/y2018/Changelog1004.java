package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.SequenceId;
import com.bitplay.persistance.domain.mon.MonRestart;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1004 {

    @ChangeSet(order = "001", id = "2018-10-04:Add sequence mon", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        String seqId = "mon";
        boolean exists = mongoTemplate.exists(Query.query(Criteria.where("_id").is(seqId)),
                SequenceId.class);
        if (!exists) {
            SequenceId sequenceId = new SequenceId();
            sequenceId.setId(seqId);
            sequenceId.setSeq(2);
            mongoTemplate.save(sequenceId);
        }
    }

    @ChangeSet(order = "002", id = "2018-10-05:Add mon restart", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        MonRestart monRestart = MonRestart.createDefaults();
        monRestart.setId(1L);
        mongoTemplate.save(monRestart);
    }

    @ChangeSet(order = "003", id = "2018-10-06:Reset mon", author = "SergeiShurmin")
    public void change03(MongoTemplate mongoTemplate) {
        mongoTemplate.dropCollection("restartMonitoringCollection");
        mongoTemplate.remove(new Query(), "monitoringCollection");

        String seqId = "mon";
        SequenceId sequenceId = new SequenceId();
        sequenceId.setId(seqId);
        sequenceId.setSeq(2);
        mongoTemplate.save(sequenceId);

        MonRestart monRestart = MonRestart.createDefaults();
        monRestart.setId(1L);
        mongoTemplate.save(monRestart);

    }
}
