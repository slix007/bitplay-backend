package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.settings.Dql;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0215 {

    @ChangeSet(order = "2019-02-15", id = "2019-02-15:Add dql level", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        final Dql dql = new Dql();
        dql.setDqlLevel(BigDecimal.ZERO);
        settings.setDql(dql);
        mongoTemplate.save(settings);
    }

}
