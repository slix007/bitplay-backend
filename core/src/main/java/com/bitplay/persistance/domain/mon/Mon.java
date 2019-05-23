package com.bitplay.persistance.domain.mon;

import com.bitplay.persistance.domain.MarketDocument;
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
@TypeAlias("mon")
@Setter
@Getter
@ToString
public class Mon extends MarketDocument {

    private String typeName;

    /**
     * Time from receiving orderBook to sending request 'move order'.
     */
    private Range before;

    /**
     * The func iterateOpenOrdersMove() has synchronized block. So that sometimes we wait for the previous moving. It shows the time before the lock, but "Too
     * often moving requests." (movingInProgress || movingDelay) hides the real frequency.
     */
    private Range waitingPrev;
    private Range wholePlacing;

    /**
     * Time from sending request 'move order' to getting an answer.
     */
    private Range waitingMarket;

    /**
     * Bitmex may have reconnects after the moving.
     */
    private Range after;

    private Integer count;

    public static Mon createDefaults() {
        Mon doc = new Mon();
        doc.before = Range.empty();
        doc.waitingPrev = Range.empty();
        doc.wholePlacing = Range.empty();
        doc.waitingMarket = Range.empty();
        doc.after = Range.empty();
        doc.count = 0;
        return doc;
    }

    public void resetMon() {
        this.before = Range.empty();
        this.waitingPrev = Range.empty();
        this.wholePlacing = Range.empty();
        this.waitingMarket = Range.empty();
        this.after = Range.empty();
        this.count = 0;
    }

    public void incCount() {
        if (count == null) {
            count = 1;
        } else {
            count++;
        }

    }
}
