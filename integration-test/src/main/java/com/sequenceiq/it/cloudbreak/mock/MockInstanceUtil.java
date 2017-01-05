package com.sequenceiq.it.cloudbreak.mock;

import java.util.ArrayList;
import java.util.Map;

import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstanceMetaData;
import com.sequenceiq.cloudbreak.cloud.model.CloudVmInstanceStatus;
import com.sequenceiq.cloudbreak.cloud.model.CloudVmMetaDataStatus;
import com.sequenceiq.cloudbreak.cloud.model.InstanceStatus;
import com.sequenceiq.cloudbreak.cloud.model.InstanceTemplate;
import com.sequenceiq.it.util.ServerAddressGenerator;

/**
 * Created by perdos on 12/8/16.
 */
public class MockInstanceUtil {

    private String mockServerAddress;

    private int sshPort;

    public MockInstanceUtil(String mockServerAddress, int sshPort) {
        this.mockServerAddress = mockServerAddress;
        this.sshPort = sshPort;
    }

    public void addInstance(Map<String, CloudVmMetaDataStatus> instanceMap, int numberOfAddedInstances) {
        ServerAddressGenerator serverAddressGenerator = new ServerAddressGenerator(numberOfAddedInstances);
        serverAddressGenerator.setFrom(instanceMap.size());

        serverAddressGenerator.iterateOver((address, number) -> {
            String instanceId = "host-" + address.replace(".", "-") + ".example.com";
            InstanceTemplate instanceTemplate = new InstanceTemplate("medium", "group", Integer.toUnsignedLong(number),
                    new ArrayList<>(), InstanceStatus.CREATED, null);
            CloudInstance cloudInstanceWithId = new CloudInstance(instanceId, instanceTemplate);
            CloudVmInstanceStatus cloudVmInstanceStatus = new CloudVmInstanceStatus(cloudInstanceWithId, InstanceStatus.STARTED);
            CloudInstanceMetaData cloudInstanceMetaData = new CloudInstanceMetaData(address, mockServerAddress, sshPort, "MOCK");
            CloudVmMetaDataStatus cloudVmMetaDataStatus = new CloudVmMetaDataStatus(cloudVmInstanceStatus, cloudInstanceMetaData);
            instanceMap.put(instanceId, cloudVmMetaDataStatus);
        });
    }

    public void terminateInstance(Map<String, CloudVmMetaDataStatus> instanceMap, String instanceId) {
        CloudVmMetaDataStatus vmMetaDataStatus = instanceMap.get(instanceId);
        InstanceTemplate oldTemplate = vmMetaDataStatus.getCloudVmInstanceStatus().getCloudInstance().getTemplate();
        InstanceTemplate newTemplate = new InstanceTemplate("medium", "group", oldTemplate.getPrivateId(),
                new ArrayList<>(), InstanceStatus.TERMINATED, null);
        CloudInstance cloudInstanceWithId = new CloudInstance(instanceId, newTemplate);
        CloudVmInstanceStatus cloudVmInstanceStatus = new CloudVmInstanceStatus(cloudInstanceWithId, InstanceStatus.TERMINATED);
        CloudVmMetaDataStatus cloudVmMetaDataStatus = new CloudVmMetaDataStatus(cloudVmInstanceStatus, vmMetaDataStatus.getMetaData());
        instanceMap.put(instanceId, cloudVmMetaDataStatus);
    }
}
