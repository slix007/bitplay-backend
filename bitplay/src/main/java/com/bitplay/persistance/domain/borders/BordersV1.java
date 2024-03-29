package com.bitplay.persistance.domain.borders;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 10/9/17.
 */
@Getter
@Setter
public class BordersV1 implements Serializable {

    private BigDecimal sumDelta = new BigDecimal(20);
}
