package com.bitplay.persistance.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Document(collection = "guiParamsCollection")
@TypeAlias("guiLiqParams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class GuiLiqParams extends AbstractParams {

    private BigDecimal bMrLiq = BigDecimal.valueOf(75);
    private BigDecimal oMrLiq = BigDecimal.valueOf(20);
    private BigDecimal bDQLOpenMin = BigDecimal.valueOf(300);
    private BigDecimal oDQLOpenMin = BigDecimal.valueOf(350);
    private BigDecimal bDQLCloseMin = BigDecimal.valueOf(150);
    private BigDecimal oDQLCloseMin = BigDecimal.valueOf(150);

}