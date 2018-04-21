package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.settings.Limits;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0318 {

    @ChangeSet(order = "001", id = "add autoBaseLvl into BordersV2", author = "SergeiShurmin")
    public void someChange1(MongoTemplate mongoTemplate) {
        // type: org.springframework.data.mongodb.core.MongoTemplate
        // Spring Data integration allows using MongoTemplate in the ChangeSet
        // example:
        final List<BorderParams> all = mongoTemplate.findAll(BorderParams.class);
        for (BorderParams borderParams : all) {
            borderParams.getBordersV2().setAutoBaseLvl(false);
            mongoTemplate.save(borderParams);
        }
    }

    @ChangeSet(order = "002", id = "2018-04-08 add Limits settings", author = "SergeiShurmin")
    public void someChange041808(MongoTemplate mongoTemplate) {
        final List<Settings> all = mongoTemplate.findAll(Settings.class);
        for (Settings settings : all) {
            settings.setLimits(Limits.createDefault());
            mongoTemplate.save(settings);
        }
    }

}
