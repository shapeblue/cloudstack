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
// Apache License, Version 2.0 (the "License"); you may not use this
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//
// Automatically generated by addcopyright.py at 04/03/2012
package com.cloud.baremetal.manager;

import com.cloud.configuration.Resource.ResourceType;
import com.cloud.dc.DataCenterVO;
import com.cloud.event.EventTypes;
import com.cloud.event.UsageEventVO;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.host.dao.HostDao;
import com.cloud.resource.ResourceManager;
import com.cloud.storage.TemplateProfile;
import com.cloud.storage.VMTemplateStorageResourceAssoc.Status;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.VMTemplateZoneVO;
import com.cloud.template.TemplateAdapter;
import com.cloud.template.TemplateAdapterBase;
import com.cloud.template.VirtualMachineTemplate.State;
import com.cloud.user.Account;
import com.cloud.utils.db.DB;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.command.user.iso.DeleteIsoCmd;
import org.apache.cloudstack.api.command.user.iso.GetUploadParamsForIsoCmd;
import org.apache.cloudstack.api.command.user.iso.RegisterIsoCmd;
import org.apache.cloudstack.api.command.user.template.RegisterTemplateCmd;
import org.apache.cloudstack.storage.command.TemplateOrVolumePostUploadCommand;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.Date;
import java.util.List;

public class BareMetalTemplateAdapter extends TemplateAdapterBase implements TemplateAdapter {
    private final static Logger s_logger = Logger.getLogger(BareMetalTemplateAdapter.class);
    @Inject
    HostDao _hostDao;
    @Inject
    ResourceManager _resourceMgr;

    @Override
    public String getName() {
        return TemplateAdapterType.BareMetal.getName();
    }

    @Override
    public TemplateProfile prepare(RegisterTemplateCmd cmd) throws ResourceAllocationException {
        return super.prepare(cmd);
    }

    @Override
    public TemplateProfile prepare(RegisterIsoCmd cmd) throws ResourceAllocationException {
        throw new CloudRuntimeException("Baremetal doesn't support ISO template");
    }

    @Override
    public TemplateProfile prepare(GetUploadParamsForIsoCmd cmd) throws ResourceAllocationException {
        throw new CloudRuntimeException("Baremetal doesn't support ISO template");
    }

    private void templateCreateUsage(VMTemplateVO template, long dcId) {
        if (template.getAccountId() != Account.ACCOUNT_ID_SYSTEM) {
            UsageEventVO usageEvent =
                new UsageEventVO(EventTypes.EVENT_TEMPLATE_CREATE, template.getAccountId(), dcId, template.getId(), template.getName(), null,
                    template.getSourceTemplateId(), 0L);
            _usageEventDao.persist(usageEvent);
        }
    }

    @Override
    public VMTemplateVO create(TemplateProfile profile) {
        VMTemplateVO template = persistTemplate(profile, State.Active);
        List<Long> zones = profile.getZoneIdList();

        // create an entry at template_store_ref with store_id = null to represent that this template is ready for use.
        TemplateDataStoreVO vmTemplateHost =
            new TemplateDataStoreVO(null, template.getId(), new Date(), 100, Status.DOWNLOADED, null, null, null, null, template.getUrl());
        this._tmpltStoreDao.persist(vmTemplateHost);

        if (zones == null) {
            List<DataCenterVO> dcs = _dcDao.listAllIncludingRemoved();
            if (dcs != null && dcs.size() > 0) {
                templateCreateUsage(template, dcs.get(0).getId());
            }
        } else {
            for (Long zoneId: zones) {
                templateCreateUsage(template, zoneId);
            }
        }

        _resourceLimitMgr.incrementResourceCount(profile.getAccountId(), ResourceType.template);
        return template;
    }

    @Override
    public List<TemplateOrVolumePostUploadCommand> createTemplateForPostUpload(TemplateProfile profile) {
        // TODO: support baremetal for postupload
        return null;
    }

    @Override
    public TemplateProfile prepareDelete(DeleteIsoCmd cmd) {
        throw new CloudRuntimeException("Baremetal doesn't support ISO, how the delete get here???");
    }

    @Override
    @DB
    public boolean delete(TemplateProfile profile) {
        VMTemplateVO template = profile.getTemplate();
        Long templateId = template.getId();
        boolean success = true;
        String zoneName;

        if (profile.getZoneIdList() != null && profile.getZoneIdList().size() > 1)
            throw new CloudRuntimeException("Operation is not supported for more than one zone id at a time");

        if (!template.isCrossZones() && profile.getZoneIdList() != null) {
            //get the first element in the list
            zoneName = profile.getZoneIdList().get(0).toString();
        } else {
            zoneName = "all zones";
        }

        s_logger.debug("Attempting to mark template host refs for template: " + template.getName() + " as destroyed in zone: " + zoneName);
        Account account = _accountDao.findByIdIncludingRemoved(template.getAccountId());
        String eventType = EventTypes.EVENT_TEMPLATE_DELETE;
        List<TemplateDataStoreVO> templateHostVOs = this._tmpltStoreDao.listByTemplate(templateId);

        for (TemplateDataStoreVO vo : templateHostVOs) {
            TemplateDataStoreVO lock = null;
            try {
                lock = _tmpltStoreDao.acquireInLockTable(vo.getId());
                if (lock == null) {
                    s_logger.debug("Failed to acquire lock when deleting templateDataStoreVO with ID: " + vo.getId());
                    success = false;
                    break;
                }

                vo.setDestroyed(true);
                _tmpltStoreDao.update(vo.getId(), vo);

            } finally {
                if (lock != null) {
                    _tmpltStoreDao.releaseFromLockTable(lock.getId());
                }
            }
        }

        if (profile.getZoneIdList() != null) {
            UsageEventVO usageEvent = new UsageEventVO(eventType, account.getId(), profile.getZoneIdList().get(0),
                                            templateId, null);
            _usageEventDao.persist(usageEvent);

            VMTemplateZoneVO templateZone = _tmpltZoneDao.findByZoneTemplate(profile.getZoneIdList().get(0), templateId);

            if (templateZone != null) {
                _tmpltZoneDao.remove(templateZone.getId());
            }
        } else {
            List<DataCenterVO> dcs = _dcDao.listAllIncludingRemoved();
            for (DataCenterVO dc : dcs) {
                UsageEventVO usageEvent = new UsageEventVO(eventType, account.getId(), dc.getId(), templateId, null);
                _usageEventDao.persist(usageEvent);
            }
        }

        if (profile.getZoneId() > 0) {
            VMTemplateZoneVO templateZone = _tmpltZoneDao.findByZoneTemplate(profile.getZoneId(), templateId);

            if (templateZone != null) {
                _tmpltZoneDao.remove(templateZone.getId());
            }
        }

        s_logger.debug("Successfully marked template host refs for template: " + template.getName() + " as destroyed in zone: " + zoneName);

        // If there are no more non-destroyed template host entries for this template, delete it
        if (success && (_tmpltStoreDao.listByTemplate(templateId).size() == 0)) {
            long accountId = template.getAccountId();

            VMTemplateVO lock = _tmpltDao.acquireInLockTable(templateId);

            try {
                if (lock == null) {
                    s_logger.debug("Failed to acquire lock when deleting template with ID: " + templateId);
                    success = false;
                } else if (_tmpltDao.remove(templateId)) {
                    // Decrement the number of templates and total secondary storage space used by the account.
                    _resourceLimitMgr.decrementResourceCount(accountId, ResourceType.template);
                    _resourceLimitMgr.recalculateResourceCount(accountId, template.getDomainId(), ResourceType.secondary_storage.getOrdinal());
                }

            } finally {
                if (lock != null) {
                    _tmpltDao.releaseFromLockTable(lock.getId());
                }
            }
            s_logger.debug("Removed template: " + template.getName() + " because all of its template host refs were marked as destroyed.");
        }

        return success;
    }
}
