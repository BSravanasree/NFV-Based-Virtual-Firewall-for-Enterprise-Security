package com.nexus.nfv.unit;

import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.firewall.FirewallDtos;
import com.nexus.nfv.firewall.FirewallRule;
import com.nexus.nfv.firewall.FirewallRuleRepository;
import com.nexus.nfv.firewall.FirewallRuleService;
import com.nexus.nfv.firewall.agent.FirewallAgentClient;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEnums;
import com.nexus.nfv.vnf.VnfRepository;
import com.nexus.nfv.vnf.VnfState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirewallRuleServiceTest {

    @Mock FirewallRuleRepository rules;
    @Mock VnfRepository vnfs;
    @Mock AuditService audit;
    @Mock FirewallAgentClient agent;
    @InjectMocks FirewallRuleService service;

    private Vnf vnf;

    @BeforeEach
    void setUp() {
        vnf = new Vnf();
        vnf.setId(1L);
        vnf.setName("fw-test");
        vnf.setVnfType(VnfEnums.VnfType.FIREWALL);
        vnf.setState(VnfState.ACTIVE);
        vnf.setRulesRevision(4);
        vnf.setSyncStatus(VnfEnums.SyncStatus.IN_SYNC);
        lenient().when(vnfs.findById(1L)).thenReturn(Optional.of(vnf));
        lenient().when(rules.findByVnfIdAndPriority(anyLong(), anyInt())).thenReturn(Optional.empty());
        lenient().when(rules.existsByVnfIdAndName(anyLong(), anyString())).thenReturn(false);
    }

    private FirewallDtos.RuleRequest req(String proto, String src, String dport) {
        return new FirewallDtos.RuleRequest("allow web", 10, FirewallRule.Action.ALLOW,
                FirewallRule.Protocol.valueOf(proto), src, "10.20.0.10", null, dport, true, true, null);
    }

    @Test
    void createNormalisesInputBumpsRevisionAndAudits() {
        FirewallDtos.RuleDto dto = service.create(1L, req("TCP", "10.10.0.11", "80"));
        ArgumentCaptor<FirewallRule> saved = ArgumentCaptor.forClass(FirewallRule.class);
        verify(rules).save(saved.capture());
        assertThat(saved.getValue().getSourceCidr()).isEqualTo("10.10.0.11/32");
        assertThat(saved.getValue().getDestinationCidr()).isEqualTo("10.20.0.10/32");
        assertThat(dto.destinationPort()).isEqualTo("80");
        assertThat(vnf.getRulesRevision()).isEqualTo(5);
        assertThat(vnf.getSyncStatus()).isEqualTo(VnfEnums.SyncStatus.PENDING);
        verify(audit).success(eq("FIREWALL_RULE_CREATED"), eq("FIREWALL"), eq("FIREWALL_RULE"), any(), anyString());
    }

    @Test
    void portsWithIcmpAreRejected() {
        assertThatThrownBy(() -> service.create(1L, req("ICMP", null, "80")))
                .isInstanceOf(ApiException.class).hasMessageContaining("TCP or UDP");
        verify(rules, never()).save(any());
    }

    @Test
    void duplicatePriorityIsAConflict() {
        FirewallRule other = new FirewallRule();
        other.setId(99L);
        other.setName("existing");
        when(rules.findByVnfIdAndPriority(1L, 10)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.create(1L, req("TCP", null, "443")))
                .isInstanceOf(ApiException.class).hasMessageContaining("Priority 10");
    }

    @Test
    void terminatedVnfIsReadOnly() {
        vnf.setState(VnfState.TERMINATED);
        assertThatThrownBy(() -> service.create(1L, req("TCP", null, "443")))
                .isInstanceOf(ApiException.class).hasMessageContaining("terminated");
    }

    @Test
    void injectionStyleInputNeverReachesPersistence() {
        assertThatThrownBy(() -> service.create(1L, req("TCP", "10.0.0.0/8 accept; flush ruleset", "80")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(1L, req("TCP", null, "80 } ; drop")))
                .isInstanceOf(ApiException.class);
        verify(rules, never()).save(any());
    }
}
