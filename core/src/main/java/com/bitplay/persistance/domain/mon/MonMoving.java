package com.bitplay.persistance.domain.mon;

import com.bitplay.persistance.domain.AbstractDocument;
import com.bitplay.persistance.domain.Range;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 9/30/18.
 */
@Document(collection = "monitoringCollection")
@TypeAlias("monMoving")
@Setter
@Getter
@ToString
public class MonMoving extends AbstractDocument {

    /**
     * Time from receiving orderBook to sending request 'move order'.
     */
    private Range before;
    /**
     * Time from sending request 'move order' to getting an answer.
     */
    private Range mainWaiting;
    private Range after;

    public static MonMoving createDefaults() {
        MonMoving doc = new MonMoving();
        doc.before = Range.empty();
        doc.mainWaiting = Range.empty();
        doc.after = Range.empty();
        return doc;
    }
}
