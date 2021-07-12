package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0604 {

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    @ChangeSet(order = "001", id = "2018-06-04:Total corr", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final List<CorrParams> all = mongoTemplate.findAll(CorrParams.class);
        for (CorrParams corrParams : all) {
            corrParams.getCorr().setMaxTotalCount(20);
            mongoTemplate.save(corrParams);
        }
    }

    @ChangeSet(order = "002", id = "2018-06-07:Total preliq", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final List<CorrParams> all = mongoTemplate.findAll(CorrParams.class);
        for (CorrParams corrParams : all) {
            corrParams.getPreliq().setMaxTotalCount(20);
            mongoTemplate.save(corrParams);
        }
    }

}
