package com.bitplay.persistance.domain;

import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
@Document(collection = "guiParamsCollection")
public abstract class AbstractParams extends AbstractDocument {

}
