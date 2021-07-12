package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.SettingsPresetRepositoryService;
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
public class Changelog0130 {

    @SuppressWarnings("Duplicates")
    @ChangeSet(order = "2019-01-30", id = "2019-01-30:Add sequence settingsPreset", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        String seqId = SettingsPresetRepositoryService.SEQ;
        boolean exists = mongoTemplate.exists(Query.query(Criteria.where("_id").is(seqId)), SequenceId.class);
        if (!exists) {
            SequenceId sequenceId = new SequenceId();
            sequenceId.setId(seqId);
            sequenceId.setSeq(0);
            mongoTemplate.save(sequenceId);
        }
    }

}
