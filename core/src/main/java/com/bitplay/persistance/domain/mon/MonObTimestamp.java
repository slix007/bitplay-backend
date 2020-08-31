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

    private Integer leftGetMin; // L_Get_OB_Delay
    private Integer leftGetMax;
    private Integer rightGetMin; // R_Get_OB_Dela
    private Integer rightGetMax;

    public MonObTimestamp() {
    }

//    L_Get_OB_Delay = L_OB_timestamp - Initial_L_OB_timestamp, где
//            L_OB_timestamp = наш timestamp стакана, Initial_L_OB_timestamp = timestamp стакана, который присылает биржа, вместе со стаканом.
//            R_Get_OB_Delay = R_OB_timestamp - Initial_R_OB_timestamp.
//    Справа от range Timestamp_diff range сделать range min/max для L_Get_OB_Delay и R_Get_OB_Delay, кнопка reset для сброса.
//    L_OB_Timestamp Diff, R_OB_Timestamp org.apache.commons.lang3.builder.Diff,
//   L_Get_OB_Delay, R_Get_OB_Delay замеряются при каждом новом получении стакана у любой из бирж, то есть после каждого расчета дельт.

    @SuppressWarnings("DuplicatedCode")
    public boolean addLeft(int val) {
        boolean changed = false;
        if (val > leftGetMax) {
            leftGetMax = val;
            changed = true;
        }
        if (val < leftGetMin) {
            leftGetMin = val;
            changed = true;
        }
        return changed;
    }

    @SuppressWarnings("DuplicatedCode")
    public boolean addRight(int val) {
        boolean changed = false;
        if (val > rightGetMax) {
            rightGetMax = val;
            changed = true;
        }
        if (val < rightGetMin) {
            rightGetMin = val;
            changed = true;
        }
        return changed;
    }

    public static MonObTimestamp createDefaults(String marketName) {
        MonObTimestamp m = new MonObTimestamp();
        m.leftGetMax = 0;
        m.leftGetMin = 9999;
        m.rightGetMax = 0;
        m.rightGetMin = 9999;
        m.setMarketName(marketName);
        return m;
    }
}
