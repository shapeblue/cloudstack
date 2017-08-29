/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cloudstack.compute.maas;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.PlugNicAnswer;
import com.cloud.agent.api.PlugNicCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartAnswer;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.UnPlugNicAnswer;
import com.cloud.agent.api.UnPlugNicCommand;
import com.cloud.agent.api.baremetal.DestroyCommand;
import com.cloud.agent.api.to.NicTO;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.baremetal.database.BaremetalRctDao;
import com.cloud.baremetal.database.BaremetalRctVO;
import com.cloud.baremetal.manager.BaremetalRct;
import com.cloud.baremetal.manager.BaremetalVlanManager;
import com.cloud.baremetal.manager.VlanType;
import com.cloud.baremetal.networkservice.BareMetalResourceBase;
import com.cloud.configuration.Config;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.network.Network;
import com.cloud.network.Networks;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.resource.ServerResource;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.google.gson.Gson;
import org.apache.log4j.Logger;

import javax.naming.ConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class MaasResourceProvider extends BareMetalResourceBase implements ServerResource {

    private static final Logger s_logger = Logger.getLogger(MaasResourceProvider.class);
    private static final String MAAS_IP = "MaasIP";
    private static final String MAAS_KEY = "MaasKey";
    private static final String MAAS_SECRET = "MaasSecret";
    private static final String MAAS_CONSUMER_KEY = "MaasConsumerKey";

    private MaasObject.MaasNode maasNode = null;
    private MaasApiClient maasApi;

    private BaremetalVlanManager vlanMgr;
    private NetworkDao networkDao;
    private HostDao hostDao;
    private VMTemplateDao templateDao;
    private HostDetailsDao hostDetailsDao;
    private BaremetalRctDao rctDao;

    private Gson gson = new Gson();

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);

        vlanMgr = (BaremetalVlanManager) params.get("vlanMgr");
        networkDao = (NetworkDao) params.get("networkDao");
        hostDao = (HostDao) params.get("hostDao");
        templateDao = (VMTemplateDao) params.get("templateDao");
        hostDetailsDao = (HostDetailsDao) params.get("hostDetailsDao");
        rctDao = (BaremetalRctDao) params.get("baremetalRctDao");

        String maasUrl = configDao.getValue(Config.ExternalBaremetalSystemUrl.key());
        String maasIp = getValue(MAAS_IP, maasUrl);
        String maasKey = getValue(MAAS_KEY, maasUrl);
        String maasSercret = getValue(MAAS_SECRET, maasUrl);
        String maasConsumerKey = getValue(MAAS_CONSUMER_KEY, maasUrl);

        int timeout = Integer.parseInt(configDao.getValue(Config.BaremetalProvisionDoneNotificationTimeout.key()));
        maasApi = new MaasApiClient(maasIp, maasKey,  maasSercret, maasConsumerKey, timeout);

        return true;
    }

    protected Answer execute(DestroyCommand cmd) {

        try {
            maasNode = maasApi.getMaasNode(maasNode.systemId);
            assert maasNode != null;
        } catch (IOException e) {
            throw new CloudRuntimeException("Unable to get MAAS node");
        }

        try {

            VirtualMachineTO vm = cmd.getVm();
            VMInstanceVO vmvo = vmDao.findById(vm.getId());
            vmvo.setHostId(hostId); ///XXX: hostid is unset, set it here so we don't get NPE downstream

            for (NicTO nic : vm.getNics()) {
                Network nw = networkDao.findByUuid(nic.getNetworkUuid());
                if (nw != null) {
                    int vlan = Integer.parseInt(Networks.BroadcastDomainType.getValue(nw.getBroadcastUri()));
                    releaseVlan(vlan, nic.isDefaultNic()? VlanType.UNTAGGED: VlanType.TAGGED);
                }
            }

            if (!doScript(_setPxeBootCommand)) {
                throw new CloudRuntimeException("Set " + _ip + " boot dev to PXE failed");
            }

            if (!maasNode.statusName.equals(MaasObject.MaasState.Ready.toString())){
                maasApi.releaseMachine(maasNode.systemId, true, false);
            }

            String hostname = "HOST-" + Long.toString(hostId);
            maasApi.updateHostname(maasNode.systemId, hostname);

        } catch (IOException e) {
            s_logger.warn("Unable to destroy the node on MAAS " + maasNode.systemId, e);
            //TODO: Move the node back to the right VLAN
            //TODO: Do we move the node to Broken state? Do we make the status as alert on Cloudstack?
            return new Answer(cmd, false, e.getMessage());
        }

        return new Answer(cmd, true, "Success");
    }

    protected StartAnswer execute(StartCommand cmd) {

        VirtualMachineTO vm = cmd.getVirtualMachine();
        VMInstanceVO vmvo = vmDao.findById(vm.getId());

        if (vmvo == null) {
            throw new CloudRuntimeException("Unable to find VM in the DB " + vm.getName());
        }

        OutputInterpreter.AllLinesParser interpreter = new OutputInterpreter.AllLinesParser();
        if (!doScript(_getStatusCommand, interpreter)) {
            return new StartAnswer(cmd, "Cannot get current power status of " + getName());
        }

        NicTO defaultNic = getDefaultNic(vm);
        if (defaultNic == null) {
            throw new CloudRuntimeException("Unable to get the default nic for VM " + vm.getId());
        }

        HostVO host = hostDao.findById(vmvo.getHostId());
        if (host == null) {
            throw new CloudRuntimeException("Unable to get the host for VM " + vm.getId());
        }

        //find the switch which is responsible for this mac
        Network nw = networkDao.findByUuid(defaultNic.getNetworkUuid());
        if (nw == null) {
            throw new CloudRuntimeException("Unable to get the network for VM " + vm.getId() + " With network ID " + defaultNic.getNetworkUuid());
        }
        int vlan = Integer.parseInt(Networks.BroadcastDomainType.getValue(nw.getBroadcastUri()));

        try {
            maasNode = maasApi.getMaasNodeByMac(_mac);
            assert maasNode != null;
        } catch (IOException e) {
            throw new CloudRuntimeException("Unable to get info from maas node");
        }

        //if the host is already deployed, just start it
        if (vmvo.getLastHostId() != null ) {
            if (vmvo.getLastHostId().equals(hostId) && maasNode.statusName.equals(MaasObject.MaasState.Deployed.toString())) {
                if (!doScript(_bootOrRebootCommand)) {
                    throw new CloudRuntimeException("IPMI reboot failed for host " + _ip);
                }
                return new StartAnswer(cmd);
            } else {
                s_logger.warn("Bad state, VM has lastHostId but MAAS is not in deployed state");
                // XXX: Do something here
                return new StartAnswer(cmd, "Unable to start VM because the baremetal is in bad state");
            }
        }

        long templateId = vmvo.getTemplateId();
        VMTemplateVO template = templateDao.findById(templateId);
        String templateUrl = template.getUrl();

        assert templateUrl != null;

        // TODO: Check if this template exisits on MAAS

        if (VirtualMachine.State.Starting != vmvo.getState()) {
                throw new CloudRuntimeException(String.format("baremetal instance[name:%s, state:%s] is not in state of Starting", vmvo.getInstanceName(), vmvo.getState()));
        }

        try {

            maasApi.updateHostname(maasNode.systemId, vm.getName());
            setupMaasBonding(maasNode, defaultNic.getMac());
            // TODO: Make this idempotent
            MaasObject.AllocateMachineParameters allocateMachineParameters = new MaasObject.AllocateMachineParameters(maasNode.systemId);
            maasApi.allocateMachine(allocateMachineParameters);

            MaasObject.DeployMachineParameters deployMachineParameters = new MaasObject.DeployMachineParameters(templateUrl);
            maasNode = maasApi.deployMachine(maasNode.systemId, deployMachineParameters);

            if (!doScript(_setDiskBootCommand)) {
                throw new CloudRuntimeException("Set " + _ip + " boot dev to Disk failed");
            }

            //TODO: Before we prepare VLANs, we must be sure that there
            // are no other VLANs on the ports just to be safe
            prepareVlan(vlan, VlanType.UNTAGGED);

            // reboot the host so that it picks up the new config from VR DHCP
            if (!doScript(_bootOrRebootCommand)) {
                throw new CloudRuntimeException("IPMI reboot failed for host " + _ip);
            }

        } catch (Exception e) {
            s_logger.error(e.getMessage(), e);

            try {
                releaseVlan(vlan, VlanType.UNTAGGED);
            } catch (Exception ex) {
                s_logger.error("Faild cleanup of VLANs ", ex);
            }

            try {
                maasNode = maasApi.getMaasNodeByMac(_mac);
                maasApi.releaseMachine(maasNode.systemId, true, false);
            } catch (IOException ex) {
                //XXX: put node into alert state, manual intervention required
                s_logger.warn("Unable to release node " + maasNode.systemId, ex);
            }

            doScript(_powerOffCommand);
            return new StartAnswer(cmd, e.getMessage());
        }

        vmvo.setState(VirtualMachine.State.Running);
        vmvo.setLastHostId(vmvo.getHostId());
        vmDao.update(vmvo.getId(), vmvo);

        s_logger.debug(String.format("received baremetal provision done notification for vm[id:%s name:%s] running on host[mac:%s, ip:%s]",
                vm.getId(), vmvo.getInstanceName(), vmvo.getPrivateMacAddress(), vmvo.getPrivateIpAddress()));

        s_logger.debug("Start bare metal vm " + vm.getName() + "successfully");
        _vmName = vm.getName();
        return new StartAnswer(cmd);
    }

    protected ReadyAnswer execute(ReadyCommand cmd) {
        return new ReadyAnswer(cmd);
    }

    protected PlugNicAnswer execute(PlugNicCommand cmd) {

        NicTO nic = cmd.getNic();
        NetworkVO nw = networkDao.findByUuid(nic.getNetworkUuid());
        int vlan = Integer.parseInt(Networks.BroadcastDomainType.getValue(nw.getBroadcastUri()));

        try {
            prepareVlan(vlan, VlanType.TAGGED);
        } catch (Exception e) {
            // recover if possible
            // TODO: What if recoery fails?

            String errMesg = "Unable to add Nic " + nic.getUuid()  + " to network " + nw.getId();
            s_logger.warn(errMesg, e);
            releaseVlan(vlan, VlanType.TAGGED);
            throw new CloudRuntimeException(errMesg, e);
        }

        return new PlugNicAnswer(cmd, true, "Nic " + nic.getUuid() +  " Added to network " + nw.getId());
    }

    protected UnPlugNicAnswer execute(UnPlugNicCommand cmd) {

        NicTO nic = cmd.getNic();
        NetworkVO nw = networkDao.findByUuid(nic.getNetworkUuid());
        int vlan = Integer.parseInt(Networks.BroadcastDomainType.getValue(nw.getBroadcastUri()));

        if (nic.isDefaultNic()) {
            throw new CloudRuntimeException("Cannot unplug default NIC for baremetal");
        }

        try {
            releaseVlan(vlan, VlanType.TAGGED);
        } catch (Exception e) {
            // recover if possible
            // TODO: What if recoery fails?

            String errMesg = "Unable to add Nic " + nic.getUuid()  + " to network " + nw.getId();
            s_logger.warn(errMesg, e);
            prepareVlan(vlan, VlanType.TAGGED);
            throw new CloudRuntimeException(errMesg, e);
        }

        return new UnPlugNicAnswer(cmd, true, "Nic " + nic.getUuid() +  " Added to network " + nw.getId());
    }

    @Override
    public boolean start() {

        MaasObject.AddMachineParameters maasMachine = new MaasObject.AddMachineParameters(_ip, _mac, _username, _password, _uuid);

        try {
            maasNode = maasApi.getMaasNodeByMac(_mac);
            assert maasNode.statusName.equals(MaasObject.MaasState.Ready.toString());

            if (maasNode == null) {
                maasNode = maasApi.addMachine(maasMachine);
                //make the default NIC DHCP
                MaasObject.MaasInterface bootInterface = maasNode.bootInterface;
                int interfaceId = bootInterface.id;
                int linkId = bootInterface.links[0].id;
                int subnetId = bootInterface.links[0].subnet.id;

                maasApi.setDhcpInterface(maasNode.systemId, interfaceId, linkId, subnetId);
            }
        } catch (IOException e) {
            String errMesg = "Error adding machine " + _ip + " Error: " + e.getMessage() + " Check MAAS and retry again";
            s_logger.warn(errMesg, e);
            throw new CloudRuntimeException(errMesg, e);
        }

        return true;
    }

    private NicTO getDefaultNic(VirtualMachineTO vm) {

        for (NicTO nic : vm.getNics()) {
            if (nic.isDefaultNic()) {
                return nic;
            }
        }
        return null;
    }

    /**
     * Returns all the MACs that are connected to the switch for this host.
     * @param node MaasNode
     * @return
     */
    protected List<String> getAllConnectedMacs(MaasObject.MaasNode node) {

        Set<String> rackMacs = new HashSet<String>();
        Set<String> maasMacs = new HashSet<String>();

        List<BaremetalRctVO> vos = rctDao.listAll();
        if (vos.isEmpty()) {
            throw new CloudRuntimeException("no rack configuration found, please call addBaremetalRct to add one");
        }

        BaremetalRctVO vo = vos.get(0);
        BaremetalRct rct = gson.fromJson(vo.getRct(), BaremetalRct.class);

        for (BaremetalRct.Rack rack : rct.getRacks()) {
            for (BaremetalRct.HostEntry host : rack.getHosts()) {
                rackMacs.add(host.getMac());
            }
        }

        for (MaasObject.MaasInterface maasInterface : node.interfaceSet) {
            maasMacs.add(maasInterface.macAddress);
        }

        maasMacs.retainAll(rackMacs);
        return new ArrayList<String>(maasMacs);
    }

    protected boolean isConnectedInterface(MaasObject.MaasNode node, String macAddress) {
        return getAllConnectedMacs(node).contains(macAddress);
    }

    public void setupMaasBonding(MaasObject.MaasNode node, String mac) throws IOException {

        MaasObject.MaasInterface bondInterface = null;
        List<Integer> phyInterfaceIds = new ArrayList<>();

        for (MaasObject.MaasInterface maasInterface: node.interfaceSet) {
            if (maasInterface.type.equals(MaasObject.InterfaceType.bond.toString())) {
                bondInterface = maasInterface;
            } else if (maasInterface.type.equals(MaasObject.InterfaceType.physical.toString())
                    && isConnectedInterface(node, maasInterface.macAddress)) {
                phyInterfaceIds.add(maasInterface.id);
            }
        }

        if (bondInterface == null) {
            assert phyInterfaceIds.size() >= 2;
            bondInterface = maasApi.createBondInterface(node.systemId, phyInterfaceIds);
        }

        MaasObject.MaasSubnet dhcpSubnet = maasApi.getDhcpSubnet();
        maasApi.setDhcpInterface(node.systemId, bondInterface.id, bondInterface.links[0].id, dhcpSubnet.id);
        maasApi.updateInterfaceMac(node.systemId, bondInterface.id, mac);
    }

    public static String getValue(String keyToMatch, String url) {
        return getValue(keyToMatch, url, true);
    }

    public static String getValue(String keyToMatch, String url, boolean throwExceptionIfNotFound) {
        String delimiter1 = ";";
        String delimiter2 = "=";

        StringTokenizer st = new StringTokenizer(url, delimiter1);

        while (st.hasMoreElements()) {
            String token = st.nextElement().toString();

            int index = token.indexOf(delimiter2);

            if (index == -1) {
                throw new RuntimeException("Invalid URL format");
            }

            String key = token.substring(0, index);

            if (key.equalsIgnoreCase(keyToMatch)) {
                return token.substring(index + delimiter2.length());
            }
        }

        if (throwExceptionIfNotFound) {
            throw new RuntimeException("Key not found in URL");
        }

        return null;
    }

    private void releaseVlan(int vlan, VlanType type){
        for (String mac : getAllConnectedMacs(maasNode)) {
            vlanMgr.releaseVlan(vlan, mac, type);
        }
    }

    private void prepareVlan(int vlan, VlanType type){
        for (String mac : getAllConnectedMacs(maasNode)) {
            vlanMgr.prepareVlan(vlan, mac, type);
        }
    }
}