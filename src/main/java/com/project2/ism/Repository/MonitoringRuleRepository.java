package com.project2.ism.Repository;

import com.project2.ism.Enum.MonitoringRuleType;
import com.project2.ism.Model.Monitoring.MonitoringRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonitoringRuleRepository extends JpaRepository<MonitoringRule, Long> {

    List<MonitoringRule> findByActiveTrue();

    List<MonitoringRule> findByActiveTrueAndRuleType(MonitoringRuleType ruleType);
}
