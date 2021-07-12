package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.SequenceId;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0910 {

    @ChangeSet(order = "001", id = "2018-09-10:Add sequence trade", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        String seqId = "trade";
        boolean exists = mongoTemplate.exists(Query.query(Criteria.where("_id").is(seqId)),
                SequenceId.class);
        if (!exists) {
            SequenceId sequenceId = new SequenceId();
            sequenceId.setId(seqId);
            sequenceId.setSeq(0);
            mongoTemplate.save(sequenceId);
        }
    }

}
