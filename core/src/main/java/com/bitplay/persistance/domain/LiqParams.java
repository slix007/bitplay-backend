package com.bitplay.persistance.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

import static com.bitplay.market.model.LiqInfo.DQL_WRONG;

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
    private BigDecimal dqlMin = BigDecimal.valueOf(10000);
    private BigDecimal dqlMax = BigDecimal.valueOf(-10000);
    private BigDecimal dmrlMin = BigDecimal.valueOf(10000);
    private BigDecimal dmrlMax = BigDecimal.valueOf(-10000);
    private BigDecimal dqlMinExtra = BigDecimal.valueOf(10000);
    private BigDecimal dqlMaxExtra = BigDecimal.valueOf(-10000);
    private BigDecimal dmrlMinExtra = BigDecimal.valueOf(10000);
    private BigDecimal dmrlMaxExtra = BigDecimal.valueOf(-10000);

    public LiqParams clone() {
        return new LiqParams(this.dqlMin, this.dqlMax, this.dmrlMin, this.dmrlMax, this.dqlMinExtra, this.dqlMaxExtra, this.dmrlMinExtra, this.dmrlMaxExtra);
    }

    private BigDecimal min(BigDecimal curr, BigDecimal v2) {
        if (v2 != null && v2.compareTo(DQL_WRONG) != 0) {
            if (curr.compareTo(v2) > 0) {
                return v2;
            }
        }
        return curr;
    }

    private BigDecimal max(BigDecimal curr, BigDecimal v2) {
        if (v2 != null && v2.compareTo(DQL_WRONG) != 0) {
            if (curr.compareTo(v2) < 0) {
                return v2;
            }
        }
        return curr;
    }

    public void updateDql(BigDecimal dql) {
        if (dql != null && dql.compareTo(DQL_WRONG) != 0) {
            dqlMin = min(dqlMin, dql);
            dqlMax = max(dqlMax, dql);
        }
    }

    public void updateDmrl(BigDecimal dmrl) {
        if (dmrl != null) {
            dmrlMin = min(dmrlMin, dmrl);
            dmrlMax = max(dmrlMax, dmrl);
        }
    }

    public void updateDqlExtra(BigDecimal dql) {
        if (dql != null && dql.compareTo(DQL_WRONG) != 0) {
            dqlMinExtra = min(dqlMinExtra, dql);
            dqlMaxExtra = max(dqlMaxExtra, dql);
        }
    }

    public void updateDmrlExtra(BigDecimal dmrl) {
        if (dmrl != null) {
            dmrlMinExtra = min(dmrlMinExtra, dmrl);
            dmrlMaxExtra = max(dmrlMaxExtra, dmrl);
        }
    }
}
