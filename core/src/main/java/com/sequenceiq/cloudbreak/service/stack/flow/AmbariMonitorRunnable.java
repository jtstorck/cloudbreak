package com.sequenceiq.cloudbreak.service.stack.flow;

import com.sequenceiq.cloudbreak.service.events.CloudbreakEventService;
import com.sequenceiq.cloudbreak.service.stack.StackService;

public class AmbariMonitorRunnable implements Runnable {
    private StackService stackService;
    private final CloudbreakEventService eventService;
    private final Long stackId;
    private final String ambariServerIp;

    public AmbariMonitorRunnable(StackService stackService, CloudbreakEventService eventService, Long stackId, String ambariServerIp) {
        this.stackService = stackService;
        this.eventService = eventService;
        this.stackId = stackId;
        this.ambariServerIp = ambariServerIp;
    }

    @Override
    public void run() {
        AmbariSubscriberClient ambariSubscriberClient = new AmbariSubscriberClient(stackService, eventService, stackId, ambariServerIp);
        ambariSubscriberClient.connect();
    }
}
