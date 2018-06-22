package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.borders.BorderDelta.DeltaCalcType;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0622 {

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    @ChangeSet(order = "001", id = "2018-06-04:Rename enum", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final List<BorderParams> all = mongoTemplate.findAll(BorderParams.class);
        for (BorderParams params: all) {
            DeltaCalcType deltaCalcType = params.getBorderDelta().getDeltaCalcType();
            if (deltaCalcType == DeltaCalcType.AVG_DELTA_EVERY_NEW_DELTA) {
                params.getBorderDelta().setDeltaCalcType(DeltaCalcType.AVG_DELTA_EVERY_NEW_DELTA_IN_PARTS);
            }
            if (deltaCalcType == DeltaCalcType.AVG_DELTA_EVERY_BORDER_COMP) {
                params.getBorderDelta().setDeltaCalcType(DeltaCalcType.AVG_DELTA_EVERY_BORDER_COMP_IN_PARTS);
            }
            mongoTemplate.save(params);
        }
    }

}
