package com.bitplay.persistance.migration.changelogs.y2019;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import java.math.BigDecimal;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Created by Sergey Shurmin on 3/31/18.
 */
@ChangeLog
public class Changelog0309 {

    @Document(collection = "bordersCollection")
    @TypeAlias("borders")
    @Data
    private static class BordersCollection0309 {

        private BordersV20309 bordersV2;

        @Data
        private static class BordersV20309 {

            private Integer step; // change int to BigDecimal
            private Integer gapStep; // change int to BigDecimal
        }
    }

    @ChangeSet(order = "2019-03-09", id = "2019-03-09:BordersV2 - change int to BigDecimal for step, gap_step.", author = "SergeiShurmin")
    public void change01(MongoTemplate mongoTemplate) {

        Query query = new Query().addCriteria(Criteria.where("_id").is(1L));
        query.fields().include("bordersV2.step");
        query.fields().include("bordersV2.gapStep");

        final BordersCollection0309 one = mongoTemplate.findOne(query, BordersCollection0309.class);
        final Integer step = one.getBordersV2().getStep();
        final Integer gapStep = one.getBordersV2().getGapStep();

        Update update = new Update();
        update.set("bordersV2.step", BigDecimal.valueOf(step));
        update.set("bordersV2.gapStep", BigDecimal.valueOf(gapStep));
        mongoTemplate.updateMulti(query, update, "bordersCollection");
    }

}
