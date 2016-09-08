package com.sequenceiq.cloudbreak.core.flow2.event;

import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;

public class InstanceRecoveryTriggerEvent extends StackEvent {
    private final String instanceId;
    private String groupName;

    public InstanceRecoveryTriggerEvent(String selector, Long stackId, String instanceId, String groupName) {
        super(selector, stackId);
        this.instanceId = instanceId;
        this.groupName = groupName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getGroupName() {
        return groupName;
    }
}
