package com.bitplay.persistance.domain.fluent;

import com.bitplay.arbitrage.dto.DeltaName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 2/25/18.
 */
@Document(collection = "dltSeries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Dlt {

    private DeltaName name;
    @JsonFormat(pattern = "HH:mm:ss.SSS")
    private Date timestamp;
    /**
     * Delta value multiplied by 100.
     */
    private Long value;

    public BigDecimal getDelta() {
        return BigDecimal.valueOf((double) value / 100);
    }
}