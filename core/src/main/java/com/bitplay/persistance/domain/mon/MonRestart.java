package com.bitplay.persistance.domain.mon;

import com.bitplay.persistance.domain.AbstractDocument;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "monitoringCollection")
@TypeAlias("monRestart")
@Setter
@Getter
@ToString
public class MonRestart extends AbstractDocument {

    private BigDecimal bTimestampDelayMax;
    private BigDecimal oTimestampDelayMax;

    public MonRestart() {
    }

    public void addBTimestampDelay(BigDecimal val) {
        if (val.compareTo(bTimestampDelayMax) > 0) {
            bTimestampDelayMax = val;
        }
    }

    public void addOTimestampDelay(BigDecimal val) {
        if (val.compareTo(oTimestampDelayMax) > 0) {
            oTimestampDelayMax = val;
        }
    }

    public static MonRestart createDefaults() {
        MonRestart monRestart = new MonRestart();
        monRestart.bTimestampDelayMax = BigDecimal.ZERO;
        monRestart.oTimestampDelayMax = BigDecimal.ZERO;
        return monRestart;
    }
}
