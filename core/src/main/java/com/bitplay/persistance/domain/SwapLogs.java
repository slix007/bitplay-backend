package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 10/26/17.
 */
@Document(collection="swapHistory")
@TypeAlias("swapLogs")
public class SwapLogs {

}
