package com.bitplay.persistance.migration.changelogs;

import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.settings.ContractMode;
import com.bitplay.persistance.domain.settings.Settings;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1017 {

    @ChangeSet(order = "001", id = "2018-10-17:Add contract mode..", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        if (settings != null) {
            ContractMode contractMode = ContractMode.parse(settings.getBitmexContractType(), settings.getOkexContractType());
            settings.setContractMode(contractMode);
            mongoTemplate.save(settings);
        }
    }

    @ChangeSet(order = "002", id = "2018-10-17:Add hedge amount to settings..", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        final GuiParams guiParams = mongoTemplate.findById(1L, GuiParams.class);
        settings.setHedgeBtc(guiParams.getHedgeAmount());
        settings.setHedgeEth(BigDecimal.ZERO);
        settings.setHedgeAuto(false);
        mongoTemplate.save(settings);
    }
}
