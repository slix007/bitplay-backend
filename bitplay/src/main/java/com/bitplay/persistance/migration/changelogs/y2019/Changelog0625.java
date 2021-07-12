package com.bitplay.persistance.migration.changelogs.y2019;

import com.bitplay.persistance.domain.CumParams;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0625 {

    @ChangeSet(order = "2019-06-25", id = "2019-06-25: counter to vert. Add unstartedVert.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {
        Query query = new Query();
        Update update = new Update();
        update.rename("counter1", "vert1");
        update.rename("counter2", "vert2");
        update.rename("completedCounter1", "completedVert1");
        update.rename("completedCounter2", "completedVert2");
        update.set("unstartedVert1", 0);
        update.set("unstartedVert2", 0);
        mongoTemplate.updateMulti(query, update, CumParams.class);
    }

    @ChangeSet(order = "2019-06-25-1", id = "2019-06-25: unset wrong", author = "SergeiShurmin")
    public void change02(MongoTemplate mongoTemplate) {
        Query query = new Query().addCriteria(
                Criteria.where("_class").is("guiParams")
                        .orOperator(Criteria.where("_class").is("guiLiqParams"),
                                (Criteria.where("_class").is("lastPriceDeviation")))
        );
        Update update = new Update();
        update.unset("unstartedVert1");
        update.unset("unstartedVert2");
        mongoTemplate.updateMulti(query, update, "guiParamsCollection");
    }

}
