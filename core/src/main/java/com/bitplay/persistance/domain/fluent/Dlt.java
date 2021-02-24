package com.bitplay.persistance.domain.fluent;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Created by Sergey Shurmin on 2/25/18.
 */
@Document(collection = "dltSeries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Dlt {

    private DeltaName name;
    @JsonFormat(pattern = "HH:mm:ss.SSS")
    @Field
    @Indexed(expireAfterSeconds = 3600 * 24 * 31) // one month
    private Date timestamp;
    /**
     * Delta value multiplied by 100.
     */
    private Long value;

    /**
     * scale as multiplier.<br>
     * mlt=100 means scale=2<br>
     * mlt=10000 means scale=4.
     */
    private int scale = 2;

    public BigDecimal getDelta() {
        return BigDecimal.valueOf(value, scale);
    }

    public Dlt(DeltaName name, Date timestamp, BigDecimal val) {
        this.name = name;
        this.timestamp = timestamp;
        final int scale = val.scale();
        final BigDecimal mlt = scaleToMlt(scale);
        this.value = val.multiply(mlt).longValue();
        this.scale = scale;
    }

    private static BigDecimal scaleToMlt(int scale) {
        if (scale == 1) {
            return BigDecimal.valueOf(10);
        } else if (scale == 3) {
            return BigDecimal.valueOf(1000);
        } else if (scale == 4) {
            return BigDecimal.valueOf(10000);
        } else if (scale == 5) {
            return BigDecimal.valueOf(100000);
        }
        return BigDecimal.valueOf(100); //default
    }
}