package com.bitplay.persistance.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection="liqParamsCollection")
@TypeAlias("liqParams")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LiqParams extends MarketDocument {
    private BigDecimal dqlMin = BigDecimal.valueOf(-10000);
    private BigDecimal dqlMax = BigDecimal.valueOf(10000);
    private BigDecimal dmrlMin = BigDecimal.valueOf(-10000);
    private BigDecimal dmrlMax = BigDecimal.valueOf(10000);

    public LiqParams clone() {
        return new LiqParams(this.dqlMin, this.dqlMax, this.dmrlMin, this.dmrlMax);
    }
}
