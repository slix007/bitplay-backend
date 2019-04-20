package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.ExtraFlag;
import com.bitplay.persistance.domain.settings.ManageType;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.util.EnumSet;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0416 {

    @ChangeSet(order = "2019-04-18", id = "2019-04-18:add manageType", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setManageType(ManageType.AUTO);
        mongoTemplate.save(settings);
    }

    @ChangeSet(order = "2019-04-19", id = "2019-04-19:add stop moving into DB", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        final EnumSet<ExtraFlag> extraFlags = EnumSet.noneOf(ExtraFlag.class);
        settings.setExtraFlags(extraFlags);
        mongoTemplate.save(settings);
    }
}
