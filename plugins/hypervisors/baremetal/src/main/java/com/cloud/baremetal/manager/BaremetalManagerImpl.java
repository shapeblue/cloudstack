// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
// Automatically generated by addcopyright.py at 01/29/2013
package com.cloud.baremetal.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.fsm.StateMachine2;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.BaremetalProvisionDoneNotificationCmd;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;

import org.apache.cloudstack.api.AddBaremetalHostCmd;

import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.fsm.StateListener;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.Event;
import com.cloud.vm.VirtualMachine.State;

public class BaremetalManagerImpl extends ManagerBase implements BaremetalManager, StateListener<State, VirtualMachine.Event, VirtualMachine>, Configurable {
    private static final Logger s_logger = Logger.getLogger(BaremetalManagerImpl.class);

    @Inject
    protected HostDao _hostDao;
    @Inject
    protected VMInstanceDao vmDao;

    public static final ConfigKey<Integer> diskEraseOnDestroy = new ConfigKey<Integer>(Integer.class, "baremetal.disk.erase.destroy", "Advanced", String.valueOf(0),
            "Erase disk on destroy baremetal VM (0=No erase, 1=Quick erase, 2=Full erase)", false, ConfigKey.Scope.Global, null);

    public static final ConfigKey<Integer> pxeVlan = new ConfigKey<Integer>(Integer.class, "baremetal.pxe.vlan", "Advanced", null,
            "VLAN of the PXE network", false, ConfigKey.Scope.Global, null);

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        VirtualMachine.State.getStateMachine().registerListener(this);
        return true;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public String getName() {
        return "Baremetal Manager";
    }

    @Override
    public boolean preStateTransitionEvent(State oldState, Event event, State newState, VirtualMachine vo, boolean status, Object opaque) {
        return false;
    }

    @Override
    public boolean postStateTransitionEvent(StateMachine2.Transition<State, Event> transition, VirtualMachine vo, boolean status, Object opaque) {
      State newState = transition.getToState();
      State oldState = transition.getCurrentState();
      if (newState != State.Starting && newState != State.Error && newState != State.Expunging) {
        return true;
      }

      if (vo.getHypervisorType() != HypervisorType.BareMetal) {
        return true;
      }

      HostVO host = _hostDao.findById(vo.getHostId());
      if (host == null) {
        s_logger.debug("Skip oldState " + oldState + " to " + "newState " + newState + " transimtion");
        return true;
      }
      _hostDao.loadDetails(host);

      if (newState == State.Starting) {
        host.setDetail("vmName", vo.getInstanceName());
        s_logger.debug("Add vmName " + host.getDetail("vmName") + " to host " + host.getId() + " details");
      } else {
        if (host.getDetail("vmName") != null && host.getDetail("vmName").equalsIgnoreCase(vo.getInstanceName())) {
          s_logger.debug("Remove vmName " + host.getDetail("vmName") + " from host " + host.getId() + " details");
          host.getDetails().remove("vmName");
        }
      }
      _hostDao.saveDetails(host);

      return true;
    }

  @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmds = new ArrayList<Class<?>>();
        cmds.add(AddBaremetalHostCmd.class);
        cmds.add(BaremetalProvisionDoneNotificationCmd.class);
        return cmds;
    }

    @Override
    public void notifyProvisionDone(BaremetalProvisionDoneNotificationCmd cmd) {
        QueryBuilder<HostVO> hq = QueryBuilder.create(HostVO.class);
        hq.and(hq.entity().getPrivateMacAddress(), SearchCriteria.Op.EQ, cmd.getMac());
        HostVO host = hq.find();
        if (host == null) {
            throw new CloudRuntimeException(String.format("cannot find host[mac:%s]", cmd.getMac()));
        }

        _hostDao.loadDetails(host);
        String vmName = host.getDetail("vmName");
        if (vmName == null) {
            throw new CloudRuntimeException(String.format("cannot find any baremetal instance running on host[mac:%s]", cmd.getMac()));
        }

        QueryBuilder<VMInstanceVO> vmq = QueryBuilder.create(VMInstanceVO.class);
        vmq.and(vmq.entity().getInstanceName(), SearchCriteria.Op.EQ, vmName);
        VMInstanceVO vm = vmq.find();

        if (vm == null) {
            throw new CloudRuntimeException(String.format("cannot find baremetal instance[name:%s]", vmName));
        }

        if (State.Starting != vm.getState()) {
            throw new CloudRuntimeException(String.format("baremetal instance[name:%s, state:%s] is not in state of Starting", vmName, vm.getState()));
        }

        vm.setState(State.Running);
        vm.setLastHostId(vm.getHostId());
        vmDao.update(vm.getId(), vm);
        s_logger.debug(String.format("received baremetal provision done notification for vm[id:%s name:%s] running on host[mac:%s, ip:%s]",
                vm.getId(), vm.getInstanceName(), host.getPrivateMacAddress(), host.getPrivateIpAddress()));
    }

    @Override
    public String getConfigComponentName() {
        return BaremetalManager.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {diskEraseOnDestroy, pxeVlan};
    }
}
