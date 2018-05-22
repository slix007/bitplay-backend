package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.RestartMonitoring;
import org.springframework.data.repository.CrudRepository;

public interface RestartMonitoringRepository extends CrudRepository<RestartMonitoring, String> {

    RestartMonitoring findFirstByDocumentId(Long id);

    default RestartMonitoring fetchRestartMonitoring() {
        RestartMonitoring firstByDocumentId = findFirstByDocumentId(1L);
        if (firstByDocumentId == null) {
            firstByDocumentId = RestartMonitoring.createDefaults();
            firstByDocumentId = saveRestartMonitoring(firstByDocumentId);
        }
        return firstByDocumentId;
    }

    default RestartMonitoring saveRestartMonitoring(RestartMonitoring restartMonitoring) {
        if (restartMonitoring.getId() == null) {
            restartMonitoring.setId(1L);
        }
        this.save(restartMonitoring);
        return restartMonitoring;
    }
}
