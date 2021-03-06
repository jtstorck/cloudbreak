package com.sequenceiq.cloudbreak.core.bootstrap.service.host;

import static com.sequenceiq.cloudbreak.core.bootstrap.service.ClusterDeletionBasedExitCriteriaModel.clusterDeletionBasedExitCriteriaModel;
import static java.util.Collections.singletonMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.model.ExposedService;
import com.sequenceiq.cloudbreak.cloud.model.AmbariDatabase;
import com.sequenceiq.cloudbreak.cloud.model.AmbariRepo;
import com.sequenceiq.cloudbreak.cloud.model.HDPRepo;
import com.sequenceiq.cloudbreak.cloud.scheduler.CancellationException;
import com.sequenceiq.cloudbreak.core.CloudbreakException;
import com.sequenceiq.cloudbreak.domain.Cluster;
import com.sequenceiq.cloudbreak.domain.ExposedServices;
import com.sequenceiq.cloudbreak.domain.HostGroup;
import com.sequenceiq.cloudbreak.domain.InstanceGroup;
import com.sequenceiq.cloudbreak.domain.InstanceMetaData;
import com.sequenceiq.cloudbreak.domain.KerberosConfig;
import com.sequenceiq.cloudbreak.domain.LdapConfig;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.domain.json.Json;
import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorCancelledException;
import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorException;
import com.sequenceiq.cloudbreak.orchestrator.host.HostOrchestrator;
import com.sequenceiq.cloudbreak.orchestrator.model.GatewayConfig;
import com.sequenceiq.cloudbreak.orchestrator.model.Node;
import com.sequenceiq.cloudbreak.orchestrator.model.SaltPillarConfig;
import com.sequenceiq.cloudbreak.orchestrator.model.SaltPillarProperties;
import com.sequenceiq.cloudbreak.repository.HostGroupRepository;
import com.sequenceiq.cloudbreak.repository.InstanceMetaDataRepository;
import com.sequenceiq.cloudbreak.repository.StackRepository;
import com.sequenceiq.cloudbreak.service.ClusterComponentConfigProvider;
import com.sequenceiq.cloudbreak.service.ComponentConfigProvider;
import com.sequenceiq.cloudbreak.service.GatewayConfigService;
import com.sequenceiq.cloudbreak.service.blueprint.BlueprintUtils;
import com.sequenceiq.cloudbreak.service.blueprint.ComponentLocatorService;
import com.sequenceiq.cloudbreak.service.cluster.AmbariAuthenticationProvider;
import com.sequenceiq.cloudbreak.service.cluster.ClusterService;
import com.sequenceiq.cloudbreak.service.cluster.flow.blueprint.BlueprintProcessor;

@Component
public class ClusterHostServiceRunner {

    @Inject
    private StackRepository stackRepository;

    @Inject
    private HostOrchestratorResolver hostOrchestratorResolver;

    @Inject
    private GatewayConfigService gatewayConfigService;

    @Inject
    private ConversionService conversionService;

    @Inject
    private ClusterService clusterService;

    @Inject
    private HostGroupRepository hostGroupRepository;

    @Inject
    private InstanceMetaDataRepository instanceMetaDataRepository;

    @Inject
    private ComponentConfigProvider componentConfigProvider;

    @Inject
    private ClusterComponentConfigProvider clusterComponentConfigProvider;

    @Inject
    private BlueprintProcessor blueprintProcessor;

    @Inject
    private ComponentLocatorService componentLocator;

    @Inject
    private BlueprintUtils blueprintUtils;

    @Inject
    private AmbariAuthenticationProvider ambariAuthenticationProvider;

    @Transactional
    public void runAmbariServices(Stack stack, Cluster cluster) throws CloudbreakException {
        try {
            Set<Node> nodes = collectNodes(stack);
            HostOrchestrator hostOrchestrator = hostOrchestratorResolver.get(stack.getOrchestrator().getType());
            GatewayConfig gatewayConfig = gatewayConfigService.getGatewayConfig(stack);
            Map<String, SaltPillarProperties> servicePillar = new HashMap<>();
            if (cluster.isSecure()) {
                Map<String, Object> krb = new HashMap<>();
                Map<String, String> kerberosConf = new HashMap<>();
                KerberosConfig kerberosConfig = cluster.getKerberosConfig();
                putIfNotNull(kerberosConf, kerberosConfig.getKerberosMasterKey(), "masterKey");
                putIfNotNull(kerberosConf, kerberosConfig.getKerberosAdmin(), "user");
                putIfNotNull(kerberosConf, kerberosConfig.getKerberosPassword(), "password");
                putIfNotNull(kerberosConf, kerberosConfig.getKerberosUrl(), "url");
                putIfNotNull(kerberosConf, kerberosConfig.getKerberosRealm(), "realm");
                krb.put("kerberos", kerberosConf);
                servicePillar.put("kerberos", new SaltPillarProperties("/kerberos/init.sls", krb));
            }
            servicePillar.put("discovery", new SaltPillarProperties("/discovery/init.sls", singletonMap("platform", stack.cloudPlatform())));
            saveGatewayPillar(gatewayConfig, cluster, servicePillar);
            AmbariRepo ambariRepo = clusterComponentConfigProvider.getAmbariRepo(cluster.getId());
            if (ambariRepo != null) {
                servicePillar.put("ambari-repo", new SaltPillarProperties("/ambari/repo.sls", singletonMap("ambari", singletonMap("repo", ambariRepo))));
            }
            AmbariDatabase ambariDb = clusterComponentConfigProvider.getAmbariDatabase(cluster.getId());
            servicePillar.put("ambari-database", new SaltPillarProperties("/ambari/database.sls", singletonMap("ambari", singletonMap("database", ambariDb))));
            LdapConfig ldapConfig = cluster.getLdapConfig();
            if (ldapConfig != null && blueprintUtils.containsComponent(cluster.getBlueprint(), "KNOX_GATEWAY")) {
                servicePillar.put("ldap", new SaltPillarProperties("/ldap/init.sls", singletonMap("ldap", ldapConfig)));
            }
            saveHDPPillar(cluster.getId(), servicePillar);
            Map<String, Object> credentials = new HashMap<>();
            credentials.put("username", ambariAuthenticationProvider.getAmbariUserName(stack.getCluster()));
            credentials.put("password", ambariAuthenticationProvider.getAmbariPassword(stack.getCluster()));
            servicePillar.put("ambari-credentials", new SaltPillarProperties("/ambari/credentials.sls", singletonMap("ambari", credentials)));
            SaltPillarConfig saltPillarConfig = new SaltPillarConfig(servicePillar);
            hostOrchestrator.runService(gatewayConfig, nodes, saltPillarConfig, clusterDeletionBasedExitCriteriaModel(stack.getId(), cluster.getId()));
        } catch (CloudbreakOrchestratorCancelledException e) {
            throw new CancellationException(e.getMessage());
        } catch (CloudbreakOrchestratorException | IOException e) {
            throw new CloudbreakException(e);
        }
    }

    private void saveGatewayPillar(GatewayConfig gatewayConfig, Cluster cluster, Map<String, SaltPillarProperties> servicePillar) throws IOException {
        Map<String, Object> gateway = new HashMap<>();
        gateway.put("address", gatewayConfig.getPublicAddress());
        gateway.put("username", cluster.getUserName());
        gateway.put("password", cluster.getPassword());
        gateway.put("topology", cluster.getKnoxTopologyName());

        Json exposedJson = cluster.getExposedKnoxServices();
        if (exposedJson != null && StringUtils.isNoneEmpty(exposedJson.getValue())) {
            List<String> exposedServices = exposedJson.get(ExposedServices.class).getServices();
            if (blueprintProcessor.componentExistsInBlueprint("HIVE_SERVER_INTERACTIVE", cluster.getBlueprint().getBlueprintText())) {
                exposedServices = exposedServices.stream().map(x -> x.equals("HIVE") ? "HIVE_INTERACTIVE" : x).collect(Collectors.toList());
            }
            gateway.put("exposed", exposedServices);
        } else {
            gateway.put("exposed", new ArrayList<>());
        }
        Map<String, List<String>> serviceLocation = componentLocator.getComponentLocation(cluster, new HashSet<>(ExposedService.getAllServiceName()));
        gateway.put("location", serviceLocation);
        servicePillar.put("gateway", new SaltPillarProperties("/gateway/init.sls", singletonMap("gateway", gateway)));
    }

    private void saveHDPPillar(Long clusterId, Map<String, SaltPillarProperties> servicePillar) {
        HDPRepo hdprepo = clusterComponentConfigProvider.getHDPRepo(clusterId);
        servicePillar.put("hdp", new SaltPillarProperties("/hdp/repo.sls", singletonMap("hdp", hdprepo)));
    }

    public Map<String, String> addAmbariServices(Long stackId, String hostGroupName, Integer scalingAdjustment) throws CloudbreakException {
        Map<String, String> candidates;
        try {
            Stack stack = stackRepository.findOneWithLists(stackId);
            Cluster cluster = stack.getCluster();
            candidates = collectUpscaleCandidates(cluster.getId(), hostGroupName, scalingAdjustment);
            Set<Node> allNodes = collectNodes(stack);
            HostOrchestrator hostOrchestrator = hostOrchestratorResolver.get(stack.getOrchestrator().getType());
            GatewayConfig gatewayConfig = gatewayConfigService.getGatewayConfig(stack);
            hostOrchestrator.runService(gatewayConfig, allNodes, new SaltPillarConfig(), clusterDeletionBasedExitCriteriaModel(stack.getId(), cluster.getId()));
        } catch (CloudbreakOrchestratorCancelledException e) {
            throw new CancellationException(e.getMessage());
        } catch (CloudbreakOrchestratorException e) {
            throw new CloudbreakException(e);
        }
        return candidates;
    }

    private Map<String, String> collectUpscaleCandidates(Long clusterId, String hostGroupName, Integer adjustment) {
        HostGroup hostGroup = hostGroupRepository.findHostGroupInClusterByName(clusterId, hostGroupName);
        if (hostGroup.getConstraint().getInstanceGroup() != null) {
            Long instanceGroupId = hostGroup.getConstraint().getInstanceGroup().getId();
            Map<String, String> hostNames = new HashMap<>();
            instanceMetaDataRepository.findUnusedHostsInInstanceGroup(instanceGroupId).stream()
                    .sorted(Comparator.comparing(InstanceMetaData::getStartDate))
                    .limit(adjustment.longValue())
                    .forEach(im -> hostNames.put(im.getDiscoveryFQDN(), im.getPrivateIp()));
            return hostNames;
        }
        return Collections.emptyMap();
    }

    private Set<Node> collectNodes(Stack stack) {
        Set<Node> agents = new HashSet<>();
        for (InstanceGroup instanceGroup : stack.getInstanceGroups()) {
            if (instanceGroup.getNodeCount() != 0) {
                for (InstanceMetaData instanceMetaData : instanceGroup.getInstanceMetaData()) {
                    agents.add(new Node(instanceMetaData.getPrivateIp(), instanceMetaData.getPublicIp(), instanceMetaData.getDiscoveryFQDN()));
                }
            }
        }
        return agents;
    }

    private void putIfNotNull(Map<String, String> context, String variable, String key) {
        if (variable != null) {
            context.put(key, variable);
        }
    }

}
