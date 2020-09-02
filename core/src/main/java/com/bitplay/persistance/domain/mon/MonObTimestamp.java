package com.bitplay.persistance.domain.mon;

import com.bitplay.persistance.domain.MarketDocumentWithId;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "monitoringCollection")
@TypeAlias("monObTimestamp")
@Setter
@Getter
@ToString
public class MonObTimestamp extends MarketDocumentWithId {

    // X(время на бирже) --> A(время получения котировки) --> B(время завершения проверки всех параметров перед сигналом)
    // XA - это отрезок *GetOb
    // AB - это отрезок *ObDiff

    private Integer minGetOb;
    private Integer maxGetOb;
    private Integer minObDiff;
    private Integer maxObDiff;
    private Integer minExecDuration;
    private Integer maxExecDuration;

    public MonObTimestamp() {
    }

    @SuppressWarnings("DuplicatedCode")
    public boolean addObDiff(int val) {
        boolean changed = false;
        if (maxObDiff == null) {
            maxObDiff = val;
            changed = true;
        }
        if (minObDiff == null) {
            minObDiff = val;
            changed = true;
        }

        if (val > maxObDiff) {
            maxObDiff = val;
            changed = true;
        }
        if (val < minObDiff) {
            minObDiff = val;
            changed = true;
        }
        return changed;
    }

    @SuppressWarnings("DuplicatedCode")
    public boolean addGetOb(int val) {
        boolean changed = false;
        if (maxGetOb == null) {
            maxGetOb = val;
            changed = true;
        }
        if (minGetOb == null) {
            minGetOb = val;
            changed = true;
        }

        if (val > maxGetOb) {
            maxGetOb = val;
            changed = true;
        }
        if (val < minGetOb) {
            minGetOb = val;
            changed = true;
        }
        return changed;
    }

    @SuppressWarnings("DuplicatedCode")
    public boolean addExecDuration(int val) {
        boolean changed = false;
        if (maxExecDuration == null) {
            maxExecDuration = val;
            changed = true;
        }
        if (minExecDuration == null) {
            minExecDuration = val;
            changed = true;
        }

        if (val > maxExecDuration) {
            maxExecDuration = val;
            changed = true;
        }
        if (val < minExecDuration) {
            minExecDuration = val;
            changed = true;
        }
        return changed;
    }


    public static MonObTimestamp createDefaults(String marketName) {
        MonObTimestamp m = new MonObTimestamp();
        m.maxGetOb = 0;
        m.minGetOb = 9999;
        m.setMarketName(marketName);
        return m;
    }

    public void reset() {
        maxGetOb = null;
        minGetOb = null;
        maxObDiff = null;
        minObDiff = null;
        maxExecDuration = null;
        minExecDuration = null;
    }
}
