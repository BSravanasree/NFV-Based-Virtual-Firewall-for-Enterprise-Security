package com.nexus.nfv.firewall;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FirewallRuleRepository extends JpaRepository<FirewallRule, Long> {

    List<FirewallRule> findByVnfIdOrderByPriorityAsc(Long vnfId);

    List<FirewallRule> findByVnfIdAndEnabledTrueOrderByPriorityAsc(Long vnfId);

    Optional<FirewallRule> findByVnfIdAndPriority(Long vnfId, int priority);

    boolean existsByVnfIdAndName(Long vnfId, String name);

    long countByEnabledTrue();

    long countByAction(FirewallRule.Action action);

    @Query("select coalesce(max(r.priority), 0) from FirewallRule r where r.vnfId = :vnfId")
    int maxPriority(Long vnfId);

    @Query("select r.action as action, r.enabled as enabled, count(r) as total from FirewallRule r group by r.action, r.enabled")
    List<ActionCount> countByActionAndEnabled();

    interface ActionCount {
        FirewallRule.Action getAction();

        boolean getEnabled();

        long getTotal();
    }
}
