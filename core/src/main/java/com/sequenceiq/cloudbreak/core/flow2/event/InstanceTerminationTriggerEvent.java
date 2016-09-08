package com.sequenceiq.cloudbreak.core.flow2.event;

import com.sequenceiq.cloudbreak.cloud.event.InstancePayload;
import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;

public class InstanceTerminationTriggerEvent extends StackEvent implements InstancePayload {
    private final String instanceId;
    private final Boolean checkState;

    public InstanceTerminationTriggerEvent(String selector, Long stackId, String instanceId, Boolean checkState) {
        super(selector, stackId);
        this.instanceId = instanceId;
        this.checkState = checkState;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    public Boolean getCheckState() {
        return checkState;
    }
}
