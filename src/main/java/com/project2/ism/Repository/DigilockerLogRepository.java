package com.project2.ism.Repository;

import com.project2.ism.Model.Logs.DigilockerLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DigilockerLogRepository
        extends JpaRepository<DigilockerLog, Long> {

    List<DigilockerLog> findByClientId(String clientId);

}
