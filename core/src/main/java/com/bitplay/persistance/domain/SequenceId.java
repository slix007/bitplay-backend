package com.bitplay.persistance.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sequence")
@Getter
@Setter
@ToString
public class SequenceId {

    @Id
    private String id;

    private long seq;

}