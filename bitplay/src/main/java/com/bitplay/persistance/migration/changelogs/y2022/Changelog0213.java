package com.bitplay.persistance.migration.changelogs.y2022;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog
public class Changelog0213 {

    @ChangeSet(order = "2022-02-13", id = "2022-02-13: add hedge cft.", author = "Sergey Shurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setHedgeCftBtc(BigDecimal.ZERO);
        settings.setHedgeCftEth(BigDecimal.ZERO);
        mongoTemplate.save(settings);
        SettingsRepositoryService.setInvalidated();
    }

}
