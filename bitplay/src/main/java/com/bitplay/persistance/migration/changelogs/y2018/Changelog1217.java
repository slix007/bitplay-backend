package com.bitplay.persistance.migration.changelogs.y2018;

import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.fluent.TradingModeState;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SettingsVolatileMode;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog1217 {

    @ChangeSet(order = "001", id = "2018-12-17: add TradingMode.VOLATILE with params.Update10", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        final Settings settings = mongoTemplate.findById(1L, Settings.class);
        settings.setTradingModeAuto(false);
        settings.setTradingModeState(new TradingModeState(TradingMode.CURRENT));

        final SettingsVolatileMode vm = new SettingsVolatileMode();
        vm.setLeftPlacingType(PlacingType.TAKER);
        vm.setRightPlacingType(PlacingType.TAKER);
        vm.setSignalDelayMs(settings.getSignalDelayMs());
        vm.setPlacingBlocks(settings.getPlacingBlocks());
        vm.setBAddBorder(BigDecimal.ZERO);
        vm.setOAddBorder(BigDecimal.ZERO);
        vm.setPosAdjustment(settings.getPosAdjustment());
        vm.setAdjustByNtUsd(false);
        vm.setVolatileDurationSec(0);
        vm.setBorderCrossDepth(BigDecimal.ZERO);
        vm.setCorrMaxTotalCount(0);
        vm.setAdjMaxTotalCount(0);
        settings.setSettingsVolatileMode(vm);

        mongoTemplate.save(settings);
    }

//    @ChangeSet(order = "002", id = "2018-12-17: add tradingModeStateSeries", author = "SergeiShurmin")
//    public void change02(MongoTemplate mongoTemplate) {
////        String seqId = TradingModeStateService.SEQ_NAME;
//        boolean exists = mongoTemplate.exists(Query.query(Criteria.where("_id").is(seqId)),
//                SequenceId.class);
//        if (!exists) {
//            SequenceId sequenceId = new SequenceId();
//            sequenceId.setId(seqId);
//            sequenceId.setSeq(0);
//            mongoTemplate.save(sequenceId);
//        }
//
//        final String collectionName = "tradingModeStateSeries";
//        if (mongoTemplate.collectionExists(collectionName)) {
//            mongoTemplate.dropCollection(collectionName);
//        }
//        CollectionOptions options = new CollectionOptions(null, 300, true);
//        mongoTemplate.createCollection(collectionName, options);
//    }

}
