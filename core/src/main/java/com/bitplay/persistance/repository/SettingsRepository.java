package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.settings.Settings;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
public interface SettingsRepository extends MongoRepository<Settings, Long> {
}
