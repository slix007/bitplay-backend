package com.bitplay.persistance;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.persistance.repository.SettingsRepository;
import com.mongodb.WriteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;

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
    private volatile boolean invalidated = true;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        settings = fetchSettings(); // in case of mongo ChangeSets
    }

    public void setInvalidated() {
        this.invalidated = true;
    }

    public Settings getSettings() {

        if (settings == null || invalidated) {
            settings = fetchSettings();
            invalidated = false;
        }

        setTransientCm();

        return settings;
    }

    private void setTransientCm() {
        settings.getPlacingBlocks().setCm(bitmexService.getCm());
        boolean eth = bitmexService.getContractType() != null
                ? bitmexService.getContractType().isEth() // current set
                : settings.getContractMode().isEth(); // saved set
        settings.getPlacingBlocks().setEth(eth);
    }

    private Settings fetchSettings() {
        Settings one = settingsRepository.findOne(1L);
        if (one == null) {
            one = Settings.createDefault();
        }
        return one;
    }

    public void saveSettings(Settings settings) {
        settingsRepository.save(settings);
        this.settings = settings;
    }

    public Settings updateVolatileDurationSec(Integer volatileDurationSec) {
        return update(null, volatileDurationSec);
    }

    public Settings updateTradingModeState(TradingMode tradingMode) {
        return update(tradingMode, null);
    }

    private Settings update(TradingMode tradingMode, Integer volatileDurationSec) {
        Query query = new Query().addCriteria(Criteria.where("_id").exists(true).andOperator(Criteria.where("_id").is(1L)));
        Update update = new Update();
        update.set("tradingModeState.timestamp", new Date());
        if (volatileDurationSec != null) {
            update.set("settingsVolatileMode.volatileDurationSec", volatileDurationSec);
        }
        if (tradingMode != null) {
            update.set("tradingModeState.tradingMode", tradingMode);
        }
        final WriteResult writeResult = mongoOperation.updateFirst(query, update, Settings.class);
        this.settings = fetchSettings();
        return this.settings;
    }

}
