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
package com.cloud.upgrade;

import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import com.cloud.storage.GuestOSHypervisorMapping;
import com.cloud.storage.GuestOSHypervisorVO;
import com.cloud.storage.GuestOSVO;
import com.cloud.storage.dao.GuestOSDao;
import com.cloud.storage.dao.GuestOSDaoImpl;
import com.cloud.storage.dao.GuestOSHypervisorDao;
import com.cloud.storage.dao.GuestOSHypervisorDaoImpl;

public class GuestOsMapper {

    final static Logger LOG = Logger.getLogger(GuestOsMapper.class);

    @Inject
    GuestOSHypervisorDao guestOSHypervisorDao;
    @Inject
    GuestOSDao guestOSDao;

    private static final String updateGuestOsHypervisorSql =
            "UPDATE `cloud`.`guest_os_hypervisor` SET guest_os_id = ? WHERE guest_os_id = ? AND hypervisor_type = ? AND hypervisor_version = ? AND guest_os_name = ? AND is_user_defined = 0 AND removed IS NULL";

    public GuestOsMapper() {
        guestOSHypervisorDao = new GuestOSHypervisorDaoImpl();
        guestOSDao = new GuestOSDaoImpl();
    }

    public void mergeDuplicates() {
        LOG.info("merging duplicate guest osses");
        Set<Set<GuestOSVO>> duplicates = findDuplicates();
        LOG.debug(String.format("merging %d sets of duplicates", duplicates.size()));
        for (Set<GuestOSVO> setOfGuestOSes : duplicates) {
            // decide which to (mark as) remove(d)
            // # highest/lowest id
            // # or is user_defined == false
            GuestOSVO guestOSVO = highestIdFrom(setOfGuestOSes);
            LOG.info(String.format("merging %d duplicates for %s ", setOfGuestOSes.size(), guestOSVO.getDisplayName()));
            makeNormative(guestOSVO, setOfGuestOSes);

        }
    }

    private void makeNormative(GuestOSVO guestOSVO, Set<GuestOSVO> setOfGuestOSes) {
        for (GuestOSVO oldGuestOs : setOfGuestOSes) {
            List<GuestOSHypervisorVO> mappings = guestOSHypervisorDao.listByGuestOsId(oldGuestOs.getId());
            copyMappings(guestOSVO, mappings);
            // // find VMs
            // // // for each VM
            // // // // set the guest_os_id to the one to keep
            // // find templates
            // // // for each template
            // // // // set the guest_os_id to the one to keep
            // // mark as removed

        }
        // set the lower id as not user defined, if that was not the premise anyway

    }

    private void copyMappings(GuestOSVO guestOSVO, List<GuestOSHypervisorVO> mappings) {
        for (GuestOSHypervisorVO mapping : mappings) {
            if (null == guestOSHypervisorDao.findByOsIdAndHypervisor(guestOSVO.getId(), mapping.getHypervisorType(), mapping.getHypervisorVersion())) {
                GuestOSHypervisorVO newMap = new GuestOSHypervisorVO();
                newMap.setGuestOsId(guestOSVO.getId());
                newMap.setGuestOsName(guestOSVO.getDisplayName());
                newMap.setHypervisorType(mapping.getHypervisorType());
                newMap.setHypervisorVersion(mapping.getHypervisorVersion());
                guestOSHypervisorDao.persist(newMap);
            }
        }
    }

    private GuestOSVO highestIdFrom(Set<GuestOSVO> setOfGuestOSes) {
        GuestOSVO rc = null;
        for (GuestOSVO guestOSVO: setOfGuestOSes) {
            if (rc == null || (guestOSVO.getId() > rc.getId() && !guestOSVO.getIsUserDefined())) {
                rc = guestOSVO;
            }
        }
        return rc;
    }

    /**
     *
     ¨¨¨
     select * from guest_os go2
      where display_name
      in (select display_name from
                 (select display_name, count(1) as count from guest_os go1 group by display_name having count > 1) tab0);
     ¨¨¨
     * and group them by display_name
     *
     *
     * @return a list of sets of duplicate
     */
    private Set<Set<GuestOSVO>> findDuplicates() {
        Set<Set<GuestOSVO>> rc = new HashSet<>();
        Set<String> names = guestOSDao.findDoubleNames();
        for (String name : names) {
            List<GuestOSVO> guestOsses = guestOSDao.listByDisplayName(name);
            if (CollectionUtils.isNotEmpty(guestOsses)) {
                rc.add(new HashSet<>(guestOsses));
            }
        }
        return rc;
    }

    private long getGuestOsId(long categoryId, String displayName) {
        GuestOSVO guestOS = guestOSDao.findByCategoryIdAndDisplayNameOrderByCreatedDesc(categoryId, displayName);
        long id = 0l;
        if (guestOS != null) {
            id = guestOS.getId();
        } else {
            LOG.warn(String.format("Unable to find the guest OS details with category id: %d and display name: %s",  + categoryId, displayName));
        }
        return id;
    }

    private long getGuestOsIdFromHypervisorMapping(GuestOSHypervisorMapping mapping) {
        GuestOSHypervisorVO guestOSHypervisorVO = guestOSHypervisorDao.findByOsNameAndHypervisorOrderByCreatedDesc(mapping.getGuestOsName(), mapping.getHypervisorType(), mapping.getHypervisorVersion());
        long id = 0;
        if (guestOSHypervisorVO != null) {
            id = guestOSHypervisorVO.getGuestOsId();
        } else {
            LOG.warn(String.format("Unable to find the guest OS hypervisor mapping details for %s", mapping.toString()));
        }
        return id;
    }

    public void addGuestOsAndHypervisorMappings(long categoryId, String displayName, List<GuestOSHypervisorMapping> mappings) {
        long guestOsId = getGuestOsId(categoryId, displayName);
        if (guestOsId == 0) {
            LOG.debug("No guest OS found with category id: " + categoryId + " and display name: " + displayName);
            if (!addGuestOs(categoryId, displayName)) {
                LOG.warn("Couldn't add the guest OS with category id: " + categoryId + " and display name: " + displayName);
                return;
            }
            guestOsId = getGuestOsId(categoryId, displayName);
        } else {
            updateToSystemDefined(guestOsId);
        }

        if (CollectionUtils.isEmpty(mappings)) {
            return;
        }

        for (final GuestOSHypervisorMapping mapping : mappings) {
            addGuestOsHypervisorMapping(mapping, guestOsId);
        }
    }

    private void updateToSystemDefined(long guestOsId) {
        GuestOSVO guestOsVo = guestOSDao.findById(guestOsId);
        guestOsVo.setIsUserDefined(false);
        guestOSDao.update(guestOsId, guestOsVo);// TODO: update is_user_defined to false
    }

    private boolean addGuestOs(long categoryId, String displayName) {
        LOG.debug("Adding guest OS with category id: " + categoryId + " and display name: " + displayName);
        GuestOSVO guestOS = new GuestOSVO();
        guestOS.setCategoryId(categoryId);
        guestOS.setDisplayName(displayName);
        guestOS = guestOSDao.persist(guestOS);
        return (guestOS != null);
    }
    public void addGuestOsHypervisorMapping(GuestOSHypervisorMapping mapping, long category, String displayName) {
        long guestOsId =  getGuestOsId(category, displayName);
        if (guestOsId == 0) {
            LOG.error(String.format("no guest os found for category %d and name %s, skipping mapping it to %s/%s", guestOsId, displayName, mapping.getHypervisorType(), mapping.getHypervisorVersion()));
        } else {
            addGuestOsHypervisorMapping(mapping, guestOsId);
        }
    }

    private void addGuestOsHypervisorMapping(GuestOSHypervisorMapping mapping, long guestOsId) {
        if(!isValidGuestOSHypervisorMapping(mapping)) {
            return;
        }

        LOG.debug("Adding guest OS hypervisor mapping - " + mapping.toString());
        GuestOSHypervisorVO guestOsMapping = new GuestOSHypervisorVO();
        guestOsMapping.setHypervisorType(mapping.getHypervisorType());
        guestOsMapping.setHypervisorVersion(mapping.getHypervisorVersion());
        guestOsMapping.setGuestOsName(mapping.getGuestOsName());
        guestOsMapping.setGuestOsId(guestOsId);
        guestOSHypervisorDao.persist(guestOsMapping);
    }

    public void updateGuestOsName(long categoryId, String oldDisplayName, String newDisplayName) {
        GuestOSVO guestOS = guestOSDao.findByCategoryIdAndDisplayNameOrderByCreatedDesc(categoryId, oldDisplayName);
        if (guestOS == null) {
            LOG.debug("Unable to update guest OS name, as there is no guest OS with category id: " + categoryId + " and display name: " + oldDisplayName);
            return;
        }

        guestOS.setDisplayName(newDisplayName);
        guestOSDao.update(guestOS.getId(), guestOS);
    }

    public void updateGuestOsNameFromMapping(String newDisplayName, GuestOSHypervisorMapping mapping) {
        if(!isValidGuestOSHypervisorMapping(mapping)) {
            return;
        }

        GuestOSHypervisorVO guestOSHypervisorVO = guestOSHypervisorDao.findByOsNameAndHypervisorOrderByCreatedDesc(mapping.getGuestOsName(), mapping.getHypervisorType(), mapping.getHypervisorVersion());
        if (guestOSHypervisorVO == null) {
            LOG.debug("Unable to update guest OS name, as there is no guest os hypervisor mapping");
            return;
        }

        long guestOsId = guestOSHypervisorVO.getGuestOsId();
        GuestOSVO guestOS = guestOSDao.findById(guestOsId);
        if (guestOS != null) {
            guestOS.setDisplayName(newDisplayName);
            guestOSDao.update(guestOS.getId(), guestOS);
        }
    }

    public void updateGuestOsIdInHypervisorMapping(Connection conn, long categoryId, String displayName, GuestOSHypervisorMapping mapping) {
        if(!isValidGuestOSHypervisorMapping(mapping)) {
            return;
        }

        long oldGuestOsId = getGuestOsIdFromHypervisorMapping(mapping);
        if (oldGuestOsId == 0) {
            LOG.debug("Unable to update guest OS in hypervisor mapping, as there is no guest os hypervisor mapping - " + mapping.toString());
            return;
        }

        long newGuestOsId = getGuestOsId(categoryId, displayName);
        if (newGuestOsId == 0) {
            LOG.debug("Unable to update guest OS id in hypervisor mapping, as there is no guest OS with category id: " + categoryId + " and display name: " + displayName);
            return;
        }

        updateGuestOsIdInMapping(conn, oldGuestOsId, newGuestOsId, mapping);
    }

    private void updateGuestOsIdInMapping(Connection conn, long oldGuestOsId, long newGuestOsId, GuestOSHypervisorMapping mapping) {
        LOG.debug("Updating guest os id: " + oldGuestOsId + " to id: " + newGuestOsId + " in hypervisor mapping - " + mapping.toString());
        try {
            PreparedStatement pstmt = conn.prepareStatement(updateGuestOsHypervisorSql);
            pstmt.setLong(1, newGuestOsId);
            pstmt.setLong(2, oldGuestOsId);
            pstmt.setString(3, mapping.getHypervisorType());
            pstmt.setString(4, mapping.getHypervisorVersion());
            pstmt.setString(5, mapping.getGuestOsName());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to update guest OS id in hypervisor mapping due to: " + e.getMessage(), e);
        }
    }

    private boolean isValidGuestOSHypervisorMapping(GuestOSHypervisorMapping mapping) {
        if (mapping != null && mapping.isValid()) {
            return true;
        }

        LOG.warn("Invalid Guest OS hypervisor mapping");
        return false;
    }
}
