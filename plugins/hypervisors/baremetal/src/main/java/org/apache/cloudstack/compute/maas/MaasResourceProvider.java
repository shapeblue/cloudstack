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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.compute.maas.MaasObject.MaasInterface;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Configurable;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.PlugNicAnswer;
import com.cloud.agent.api.PlugNicCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartAnswer;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.UnPlugNicAnswer;
import com.cloud.agent.api.UnPlugNicCommand;
import com.cloud.agent.api.baremetal.DestroyCommand;
import com.cloud.agent.api.to.NicTO;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.api.query.dao.UserVmJoinDao;
import com.cloud.api.query.vo.UserVmJoinVO;
import com.cloud.baremetal.database.BaremetalRctDao;
import com.cloud.baremetal.database.BaremetalRctVO;
import com.cloud.baremetal.manager.BareMetalResource;
import com.cloud.baremetal.manager.BaremetalManagerImpl;
import com.cloud.baremetal.manager.BaremetalRct;
import com.cloud.baremetal.manager.BaremetalVlanManager;
import com.cloud.baremetal.manager.VlanType;
import com.cloud.baremetal.networkservice.BareMetalResourceBase;
import com.cloud.host.DetailVO;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.network.Network;
import com.cloud.network.Networks;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.gson.Gson;

@Configurable
public class MaasResourceProvider extends BareMetalResourceBase implements BareMetalResource {

    private static final Logger s_logger = Logger.getLogger(MaasResourceProvider.class);
    private static final String MAAS_ID_KEY = "MaasSystemId";

    private String maasUniqueId = null;
    private MaasObject.MaasNode maasNode = null;
    private MaasApiClient maasApi;

    protected static ConfigurationDao configDao;
    protected static VMInstanceDao vmDao;

    private static BaremetalVlanManager vlanMgr;
    private static NetworkDao networkDao;
    private static HostDao hostDao;
    private static VMTemplateDao templateDao;
    private static HostDetailsDao hostDetailsDao;
    private static MaasManager maasManager;
    private static BaremetalRctDao rctDao;
    private static AgentManager agentMgr;
    private static UserVmJoinDao userVmJoinDao;

    @Inject protected ConfigurationDao _configDao;
    @Inject protected VMInstanceDao _vmDao;

    @Inject private BaremetalVlanManager _vlanMgr;
    @Inject private NetworkDao _networkDao;
    @Inject private HostDao _hostDao;
    @Inject private VMTemplateDao _templateDao;
    @Inject private HostDetailsDao _hostDetailsDao;
    @Inject private MaasManager _maasManager;
    @Inject private BaremetalRctDao _rctDao;
    @Inject private AgentManager _agentMgr;
    @Inject private UserVmJoinDao _userVmJoinDao;
    private MaasHostListner hostListner;

    private Gson gson = new Gson();

    @PostConstruct
    void init() {
        if (_configDao != null) {
            configDao = _configDao;
        }
        if (_vmDao != null) {
            vmDao = _vmDao;
        }
        if (_vlanMgr != null) {
            vlanMgr = _vlanMgr;
        }
        if (_networkDao != null) {
            networkDao = _networkDao;
        }
        if (_hostDao != null) {
            hostDao = _hostDao;
        }
        if (_templateDao != null) {
            templateDao = _templateDao;
        }
        if (_hostDetailsDao != null) {
            hostDetailsDao = _hostDetailsDao;
        }
        if (_maasManager != null) {
            maasManager = _maasManager;
        }
        if (_rctDao != null) {
            rctDao = _rctDao;
        }
        if (_agentMgr != null) {
            agentMgr = _agentMgr;
        }
        if (_userVmJoinDao != null) {
            userVmJoinDao = _userVmJoinDao;
        }
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        ipmiIface = "lanplus";
        configure(name, params, configDao, vmDao);

        if (params.keySet().size() == 0) {
            return true;
        }

        // MaaS Import Node
        if (ApiConstants.BAREMETAL_MAAS_ACTION_IMPORT.equals((String) params.get(ApiConstants.BAREMETAL_MAAS_ACTION))) {
            maasUniqueId = (String) params.get(ApiConstants.BAREMETAL_MAAS_NODE_ID);

            if (maasUniqueId == null) {
                throw new ConfigurationException("Unable to get the host unique id");
            }
        }

        if (StringUtils.isNotEmpty((String) params.get("MaasSystemId")) && StringUtils.isEmpty(maasUniqueId)) {
            maasUniqueId = (String) params.get("MaasSystemId");
        }

        if (configDao == null) {
            return true;
        }

        maasApi = maasManager.getMaasApiClient(Long.parseLong(_cluster));
        hostListner = new MaasHostListner(this);
        agentMgr.registerForHostEvents(hostListner, true, false, true);

        return true;
    }

    @Override
    public Type getType() {
        return com.cloud.host.Host.Type.Routing;
    }

    @Override
    public StartupCommand[] initialize() {
        StartupRoutingCommand cmd = new StartupRoutingCommand(0, 0, 0, 0, null, Hypervisor.HypervisorType.BareMetal,
            new HashMap<String, String>());

        cmd.setDataCenter(_zone);
        cmd.setPod(_pod);
        cmd.setCluster(_cluster);
        cmd.setGuid(_uuid);
        cmd.setName(maasUniqueId);
        cmd.setPrivateIpAddress(_ip);
        cmd.setStorageIpAddress(_ip);
        cmd.setVersion(BareMetalResourceBase.class.getPackage().getImplementationVersion());
        cmd.setCpus((int) _cpuNum);
        cmd.setSpeed(_cpuCapacity);
        cmd.setMemory(_memCapacity);
        cmd.setPrivateMacAddress(_mac);
        cmd.setPublicMacAddress(_mac);
        return new StartupCommand[] { cmd };
    }

    protected Answer execute(DestroyCommand cmd) {

        try {
            maasNode = maasApi.getMaasNode(maasNode.getSystemId());
            assert maasNode != null;
        } catch (IOException e) {
            throw new CloudRuntimeException("Unable to get MAAS node", e);
        }

        try {
            VirtualMachineTO vm = cmd.getVm();
            VMInstanceVO vmvo = vmDao.findById(vm.getId());
            vmvo.setHostId(hostId); //hostid is unset, set it here so we don't get NPE downstream

            for (NicTO nic : vm.getNics()) {
                Network nw = networkDao.findByUuid(nic.getNetworkUuid());
                if (nw != null) {
                    int vlan = Integer.parseInt(Networks.BroadcastDomainType.getValue(nw.getBroadcastUri()));
                    releaseVlan(vlan, nic.isDefaultNic()? VlanType.UNTAGGED: VlanType.TAGGED, false);
                }
            }

            if (!doScript(_setPxeBootCommand)) {
                throw new CloudRuntimeException("Set " + _ip + " boot dev to PXE failed");
            }

            if (!doScript(_powerOffCommand)) {
                throw new CloudRuntimeException("Unable to power off " + _ip);
            }

            if (BaremetalManagerImpl.pxeVlan.value() != null) {
                prepareVlan(BaremetalManagerImpl.pxeVlan.value(), VlanType.UNTAGGED);
            }

            UserVmJoinVO uservm = userVmJoinDao.findById(vmvo.getId());

            maasApi.removeTagFromMachine(maasNode.getSystemId(), "accountid_" + uservm.getAccountUuid());
            maasApi.removeTagFromMachine(maasNode.getSystemId(), "domainid_" + uservm.getDomainUuid());

            if (StringUtils.isNotEmpty(uservm.getProjectUuid())) {
                maasApi.removeTagFromMachine(maasNode.getSystemId(), "projectid_" + uservm.getProjectUuid());
            }

            if (!maasNode.getStatusName().equals(MaasObject.MaasState.Ready.toString())){
                Integer eraseStrategy = BaremetalManagerImpl.diskEraseOnDestroy.value();
                boolean eraseDisk = eraseStrategy == 1 || eraseStrategy == 2;
                boolean fullErase = eraseStrategy == 2;
                maasApi.releaseMachine(maasNode.getSystemId(), eraseDisk, fullErase);
            }

            String hostname = "HOST-" + Long.toString(hostId);
            maasApi.updateHostname(maasNode.getSystemId(), hostname);

        } catch (IOException e) {
            s_logger.warn("Unable to destroy the node on MAAS " + maasNode.getSystemId(), e);
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
            maasNode = maasApi.getMaasNode(maasNode.getSystemId());
            assert maasNode != null;
        } catch (IOException e) {
            throw new CloudRuntimeException("Unable to get info from maas node");
        }

        //if the host is already deployed, just start it
        if (vmvo.getLastHostId() != null ) {
            if (vmvo.getLastHostId().equals(hostId) && maasNode.getStatusName().equals(MaasObject.MaasState.Deployed.toString())) {
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

        //deploy OS on the host using MAAS
        long templateId = vmvo.getTemplateId();
        VMTemplateVO template = templateDao.findById(templateId);
        String templateUrl = template.getUrl();

        assert templateUrl != null;

        checkTemplateOnMaas(templateUrl);

        if (VirtualMachine.State.Starting != vmvo.getState()) {
                throw new CloudRuntimeException(String.format("baremetal instance[name:%s, state:%s] is not in state of Starting", vmvo.getInstanceName(), vmvo.getState()));
        }

        if (!maasNode.statusName.equals(MaasObject.MaasState.Ready.toString())) {
            throw new CloudRuntimeException(String.format("Maas State is not in ready %s %s", vmvo.getInstanceName(), maasNode.systemId));
        }

        try {

            // Before we prepare VLANs, we must be sure that there
            // are no other VLANs on the ports just to be safe
            if (BaremetalManagerImpl.pxeVlan.value() != null) {
                releaseVlan(BaremetalManagerImpl.pxeVlan.value(), VlanType.UNTAGGED, true);
                prepareVlan(BaremetalManagerImpl.pxeVlan.value(), VlanType.UNTAGGED);
            }

            maasApi.updateHostname(maasNode.getSystemId(), vm.getName());
            setupMaasBonding(maasNode, defaultNic.getMac());

            MaasObject.AllocateMachineParameters allocateMachineParameters = new MaasObject.AllocateMachineParameters(maasNode.getSystemId());
            maasApi.allocateMachine(allocateMachineParameters);

            UserVmJoinVO uservm = userVmJoinDao.findById(vmvo.getId());

            maasApi.addTagToMachine(maasNode.getSystemId(), "accountid_" + uservm.getAccountUuid());
            maasApi.addTagToMachine(maasNode.getSystemId(), "domainid_" + uservm.getDomainUuid());

            if (StringUtils.isNotEmpty(uservm.getProjectUuid())) {
                maasApi.addTagToMachine(maasNode.getSystemId(), "projectid_" + uservm.getProjectUuid());
            }

            MaasObject.DeployMachineParameters deployMachineParameters = new MaasObject.DeployMachineParameters(templateUrl);
            maasNode = maasApi.deployMachine(maasNode.getSystemId(), deployMachineParameters);

            if (!doScript(_setDiskBootCommand)) {
                throw new CloudRuntimeException("Set " + _ip + " boot dev to Disk failed");
            }

            // Before we prepare VLANs, we must to remove
            // default PXE VLAN on the ports just to be safe
            if (BaremetalManagerImpl.pxeVlan.value() != null) {
                releaseVlan(BaremetalManagerImpl.pxeVlan.value(), VlanType.UNTAGGED, false);
            }
            prepareVlan(vlan, VlanType.UNTAGGED);

            // reboot the host so that it picks up the new config from VR DHCP
            if (!doScript(_bootOrRebootCommand)) {
                throw new CloudRuntimeException("IPMI reboot failed for host " + _ip);
            }

        } catch (Exception e) {
            s_logger.error(e.getMessage(), e);

            try {
                releaseVlan(vlan, VlanType.UNTAGGED, false);
            } catch (Exception ex) {
                s_logger.error("Failed cleanup of VLANs ", ex);
            }

            try {
                maasNode = maasApi.getMaasNode(maasNode.getSystemId());
                Integer eraseStrategy = BaremetalManagerImpl.diskEraseOnDestroy.value();
                boolean eraseDisk = eraseStrategy == 1 || eraseStrategy == 2;
                boolean fullErase = eraseStrategy == 2;
                maasApi.releaseMachine(maasNode.getSystemId(), eraseDisk, fullErase);
            } catch (IOException ex) {
                //XXX: put node into alert state, manual intervention required
                s_logger.error("Unable to release node " + maasNode.getSystemId(), ex);
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

    private void checkTemplateOnMaas(String templateUrl) {
        try {
            boolean imgFound = false;
            for (MaasObject.BootImage img: maasApi.listImages()) {
                if (img.name.contains(templateUrl)) {
                    imgFound = true;
                    break;
                }
            }

            if (!imgFound) {
                throw new CloudRuntimeException("Template " + templateUrl + " Not found in MAAS");
            }
        } catch (IOException e) {
            throw new CloudRuntimeException("Unable to list boot images for MAAS", e);
        }
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
            String errMesg = "Unable to add Nic " + nic.getUuid()  + " to network " + nw.getId();
            s_logger.warn(errMesg, e);
            releaseVlan(vlan, VlanType.TAGGED, false);
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
            releaseVlan(vlan, VlanType.TAGGED, false);
        } catch (Exception e) {
            String errMesg = "Unable to add Nic " + nic.getUuid()  + " to network " + nw.getId();
            s_logger.warn(errMesg, e);
            prepareVlan(vlan, VlanType.TAGGED);
            throw new CloudRuntimeException(errMesg, e);
        }

        return new UnPlugNicAnswer(cmd, true, "Nic " + nic.getUuid() +  " Added to network " + nw.getId());
    }

    @Override
    public boolean start() {
        if (_zone == null) {
            return true;
        }
        if (configDao == null) {
            return true;
        }

        // Node Create
        if (StringUtils.isEmpty(maasUniqueId)) {
            MaasObject.AddMachineParameters maasMachine = new MaasObject.AddMachineParameters(_ip, _mac, _username, _password, _uuid);

            try {
                if (hostId == null) {
                    addMassMachine(maasMachine);
                } else {
                    DetailVO maasNodeId = hostDetailsDao.findDetail(hostId, MAAS_ID_KEY);
                    if (maasNodeId != null) {
                        maasNode = maasApi.getMaasNode(maasNodeId.getValue());
                        if(maasNode == null) {
                            maasUniqueId = maasNode.getSystemId();
                            addMassMachine(maasMachine);
                        }
                    }
                }
            } catch (IOException e) {
                String errMesg = "Error adding machine " + _ip + " Error: " + e.getMessage() + " Check MAAS and remove host if already added and retry again";
                s_logger.warn(errMesg, e);
                throw new CloudRuntimeException(errMesg, e);
            }

            HostVO host = hostDao.findByGuid(_uuid);
            if (host != null) {
                updateHostAddedDetails(host.getId());
            }
        }

        // Node Import
        else {
            try {
                maasNode = maasApi.getMaasNode(maasUniqueId);
                if(maasNode != null) {
                    maasUniqueId = maasNode.getSystemId();
                    _cpuNum = maasNode.getCpuCount();
                    _cpuCapacity = maasNode.getCpuSpeed();
                    _memCapacity = maasNode.getMemory() * 1024 * 1024;

                    MaasInterface minterface = Arrays.asList(maasNode.getInterfaceSet())
                        .stream()
                        .filter(i -> i.type.equals("physical"))
                        .findFirst()
                        .get();

                    if (minterface != null) {
                        _mac = minterface.macAddress;
                    }
                }
            } catch (IOException e) {
                String errMesg = "Error adding machine " + maasUniqueId + " Error: " + e.getMessage() + " Check MAAS and add the selecte node.";
                s_logger.warn(errMesg, e);
                throw new CloudRuntimeException(errMesg, e);
            }
        }

        return true;
    }

    private void addMassMachine(MaasObject.AddMachineParameters maasMachine) throws IOException {
        if (BaremetalManagerImpl.pxeVlan.value() != null) {
            vlanMgr.prepareVlan(BaremetalManagerImpl.pxeVlan.value(), _mac, VlanType.UNTAGGED);
        }

        maasNode = maasApi.addMachine(maasMachine);

        //make the default NIC DHCP
        MaasObject.MaasInterface bootInterface = maasNode.getBootInterface();
        int interfaceId = bootInterface.id;
        int linkId = bootInterface.links[0].id;
        int subnetId = bootInterface.links[0].subnet.id;
        maasApi.setInterface(maasNode.getSystemId(), interfaceId, linkId, subnetId, true);

        //make sure all the other interfaces are on the same fabric/vlan to enable bonding
        for (MaasObject.MaasInterface iface : maasNode.getInterfaceSet()) {
            if (!iface.macAddress.equals(bootInterface.macAddress)) {
                if (BaremetalManagerImpl.pxeVlan.value() != null) {
                    vlanMgr.prepareVlan(BaremetalManagerImpl.pxeVlan.value(), iface.macAddress, VlanType.UNTAGGED);
                }
                Integer lId = null;
                if (iface.links != null && iface.links.length > 0) {
                    lId = iface.links[0].id;
                }
                maasApi.setInterface(maasNode.getSystemId(), iface.id, lId, subnetId, false);
            }
        }

        //update maas node
        maasNode = maasApi.getMaasNode(maasNode.getSystemId());
    }

    public void updateHostAddedDetails(long hostId) {
        if (this.hostId == null) {
            this.hostId = hostId;
            DetailVO maasIdDetail = new DetailVO(hostId, MAAS_ID_KEY, maasNode.getSystemId());
            hostDetailsDao.persist(maasIdDetail);
        }
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
        maasApi.setInterface(node.systemId, bondInterface.id, bondInterface.links[0].id, dhcpSubnet.id, true);
        maasApi.updateInterfaceMac(node.systemId, bondInterface.id, mac);
    }

    private void releaseVlan(int vlan, VlanType type, boolean releaseAll) {
        for (String mac : getAllConnectedMacs(maasNode)) {
            if (releaseAll) {
                vlanMgr.releaseAllVlan(mac, type);
            } else {
                vlanMgr.releaseVlan(vlan, mac, type);
            }
        }
    }

    private void prepareVlan(int vlan, VlanType type) {
        for (String mac : getAllConnectedMacs(maasNode)) {
            vlanMgr.prepareVlan(vlan, mac, type);
        }
    }
}
