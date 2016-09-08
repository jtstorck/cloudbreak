package com.sequenceiq.cloudbreak.core.flow2.chain;

import com.sequenceiq.cloudbreak.cloud.event.Selectable;
import com.sequenceiq.cloudbreak.core.flow2.FlowTriggers;
import com.sequenceiq.cloudbreak.core.flow2.event.ClusterScaleTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.event.InstanceRecoveryTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.event.InstanceTerminationTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.event.StackScaleTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.event.StackSyncTriggerEvent;
import com.sequenceiq.cloudbreak.domain.Cluster;
import com.sequenceiq.cloudbreak.domain.HostGroup;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class RecoverInstanceFlowEventChainFactory implements FlowEventChainFactory<InstanceRecoveryTriggerEvent> {

    @Inject
    private StackService stackService;

    @Inject
    private HostGroupService hostGroupService;

    @Override
    public String initEvent() {
        return FlowTriggers.RECOVER_INSTANCE_TRIGGER_EVENT;
    }

    @Override
    public Queue<Selectable> createFlowTriggerEventQueue(InstanceRecoveryTriggerEvent event) {
        Stack stack = stackService.getById(event.getStackId());
        Cluster cluster = stack.getCluster();
        Queue<Selectable> flowEventChain = new ConcurrentLinkedQueue<>();
        flowEventChain.add(new InstanceTerminationTriggerEvent(FlowTriggers.REMOVE_INSTANCE_TRIGGER_EVENT,
                event.getStackId(), event.getInstanceId(), false));
        String instanceGroupName = event.getGroupName();
        flowEventChain.add(new StackScaleTriggerEvent(FlowTriggers.STACK_UPSCALE_TRIGGER_EVENT, event.getStackId(),
                instanceGroupName, 1));
        HostGroup hostGroup = hostGroupService.getByClusterIdAndInstanceGroupName(cluster.getId(), instanceGroupName);
        flowEventChain.add(new ClusterScaleTriggerEvent(
                FlowTriggers.CLUSTER_UPSCALE_TRIGGER_EVENT, stack.getId(), hostGroup.getName(), 1));
        return flowEventChain;
    }
}
