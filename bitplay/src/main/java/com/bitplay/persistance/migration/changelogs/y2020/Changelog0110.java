package com.bitplay.persistance.migration.changelogs.y2020;

import com.bitplay.persistance.domain.GuiLiqParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0110 {

    @ChangeSet(order = "2020-01-10", id = "2020-01-10: move dql params", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {

        Query q = new Query(Criteria.where("_id").is(3L));
        final List<GuiLiqParams> guiParamsCollection = mongoTemplate.find(q, GuiLiqParams.class, "guiParamsCollection");
        if (guiParamsCollection.size() > 0) {
            final GuiLiqParams guiLiqParams = guiParamsCollection.get(0);

            Query query = new Query();
            Update update = new Update();
            update.set("dql.bMrLiq", guiLiqParams.getBMrLiq());
            update.set("dql.oMrLiq", guiLiqParams.getOMrLiq());
            update.set("dql.bDQLOpenMin", guiLiqParams.getBDQLOpenMin());
            update.set("dql.oDQLOpenMin", guiLiqParams.getODQLOpenMin());
            update.set("dql.bDQLCloseMin", guiLiqParams.getBDQLCloseMin());
            update.set("dql.oDQLCloseMin", guiLiqParams.getODQLCloseMin());
            mongoTemplate.updateMulti(query, update, "settingsCollection");
        } else {
            Query query = new Query();
            Update update = new Update();
            update.set("dql.bMrLiq", BigDecimal.valueOf(75));
            update.set("dql.oMrLiq", BigDecimal.valueOf(20));
            update.set("dql.bDQLOpenMin", BigDecimal.valueOf(300));
            update.set("dql.oDQLOpenMin", BigDecimal.valueOf(350));
            update.set("dql.bDQLCloseMin", BigDecimal.valueOf(150));
            update.set("dql.oDQLCloseMin", BigDecimal.valueOf(150));
            mongoTemplate.updateMulti(query, update, "settingsCollection");
        }
    }

}
