package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0418 {

    @ChangeSet(order = "001", id = "add avg_delta into BorderParams1", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final List<BorderParams> all = mongoTemplate.findAll(BorderParams.class);
        for (BorderParams borderParams : all) {
            borderParams.setBorderDelta(BorderDelta.createDefault());
            mongoTemplate.save(borderParams);
        }
    }

}
