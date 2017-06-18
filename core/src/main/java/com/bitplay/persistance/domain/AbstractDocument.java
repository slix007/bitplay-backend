package com.bitplay.persistance.domain;

import org.springframework.data.annotation.Id;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public class AbstractDocument {

    @Id
    private Long documentId;

    public void setId(Long id) {
        this.documentId = id;
    }

    public Long getId() {
        return documentId;
    }

}
