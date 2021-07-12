package com.bitplay.persistance.dao;

import com.bitplay.persistance.exception.SequenceException;

public interface SequenceDao {

    long getNextSequenceId(String key) throws SequenceException;

}
