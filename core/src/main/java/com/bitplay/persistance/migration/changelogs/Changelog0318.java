package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.BorderParams;
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

}
