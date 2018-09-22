package com.bitplay.persistance;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.persistance.repository.SettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Service
public class SettingsRepositoryService {

    private MongoOperations mongoOperation;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    public SettingsRepositoryService(MongoOperations mongoOperation) {
        this.mongoOperation = mongoOperation;
    }

    private volatile Settings settings;

    public Settings getSettings() {

        if (settings == null) {
            settings = fetchSettings();
        }

        settings.getPlacingBlocks().setBitmexBlockFactor(bitmexService.getCm());

        return settings;
    }

    private Settings fetchSettings() {
        Settings one = settingsRepository.findOne(1L);
        if (one == null) {
            one = Settings.createDefault();
        }
        if (one.getPlacingBlocks() == null) {
            one.setPlacingBlocks(PlacingBlocks.createDefault());
        }
        if (one.getBitmexSysOverloadArgs() == null) {
            one.setBitmexSysOverloadArgs(SysOverloadArgs.defaults());
        }
        if (one.getOkexSysOverloadArgs() == null) {
            one.setOkexSysOverloadArgs(SysOverloadArgs.defaults());
        }
        return one;
    }

    public void saveSettings(Settings settings) {
        settingsRepository.save(settings);
        this.settings = settings;
    }

    public Settings updateSysOverloadArgs(SysOverloadArgs sysOverloadArgs) {

        Query query = new Query();
        query.addCriteria(Criteria
                .where("documentId").exists(true)
                .andOperator(Criteria.where("documentId").is(1L)));

        Update update = new Update();

        //update age to 11
        update.set("sysOverloadArgs", sysOverloadArgs);

        mongoOperation.upsert(query, update, Settings.class);

        Settings settings = mongoOperation.findOne(query, Settings.class);

//        System.out.println("settings - " + settings);

        return settings;
    }

}
