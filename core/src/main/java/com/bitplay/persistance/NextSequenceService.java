package com.bitplay.persistance;

import com.bitplay.persistance.domain.AbstractDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class NextSequenceService {
    @Autowired
    private MongoOperations mongo;

    public Long getNextSequence(String seqName) {
        AbstractDocument doc = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(seqName)),
                new Update().inc("seq",1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                AbstractDocument.class);
        return doc.getId();
    }
}