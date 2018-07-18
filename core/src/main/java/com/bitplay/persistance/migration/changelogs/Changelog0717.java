package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.GuiLiqParams;
import com.bitplay.persistance.domain.GuiParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0717 {

    @ChangeSet(order = "2018-07-11-1", id = "2018-07-11:CumParams", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final GuiParams guiParams = mongoTemplate.findById(1L, GuiParams.class);
        CumParams cumParams = new CumParams();
        BeanUtils.copyProperties(guiParams, cumParams);
        cumParams.setId(2L);
        mongoTemplate.save(cumParams);
    }

    @ChangeSet(order = "2018-07-11-3", id = "2018-07-11:CumParams3", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        GuiParams guiParams = mongoTemplate.findById(1L, GuiParams.class);
        CumParams cumParams = mongoTemplate.findById(2L, CumParams.class);
        if (guiParams == null) {
            guiParams = new GuiParams();
            guiParams.setId(1L);
            mongoTemplate.save(guiParams);
        }
        if (cumParams == null) {
            cumParams = new CumParams();
            cumParams.setId(2L);
            mongoTemplate.save(cumParams);
        }
    }

    @ChangeSet(order = "2018-07-11-4", id = "2018-07-11:GuiLiqParams", author = "SergeiShurmin")
    public void change03(MongoTemplate mongoTemplate) {
        GuiLiqParams guiLiqParams = mongoTemplate.findById(3L, GuiLiqParams.class);
        if (guiLiqParams == null) {
            final GuiParams guiParams = mongoTemplate.findById(1L, GuiParams.class);
            guiLiqParams = new GuiLiqParams();
            BeanUtils.copyProperties(guiParams, guiLiqParams);
            guiLiqParams.setId(3L);
            mongoTemplate.save(guiLiqParams);
        }
    }

}
