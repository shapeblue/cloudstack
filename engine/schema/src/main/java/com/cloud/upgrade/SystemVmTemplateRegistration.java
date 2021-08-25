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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.dao.ConfigurationDaoImpl;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDao;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDaoImpl;
import org.apache.cloudstack.storage.datastore.db.ImageStoreVO;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.apache.log4j.Logger;
import org.ini4j.Ini;

import com.cloud.dc.ClusterVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.ClusterDaoImpl;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DataCenterDaoImpl;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.Storage;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.VMTemplateStorageResourceAssoc;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VMTemplateDaoImpl;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.upgrade.dao.BasicTemplateDataStoreDaoImpl;
import com.cloud.user.Account;
import com.cloud.utils.DateUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.UriUtils;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import com.cloud.vm.dao.VMInstanceDao;
import com.cloud.vm.dao.VMInstanceDaoImpl;

public class SystemVmTemplateRegistration {
    private static final Logger LOGGER = Logger.getLogger(SystemVmTemplateRegistration.class);
    private static final String MOUNT_COMMAND = "sudo mount -t nfs %s %s";
    private static final String UMOUNT_COMMAND = "sudo umount %s";
    private static final String HASH_ALGORITHM = "MD5";
    private static final String RELATIVE_TEMPLATE_PATH = "./engine/schema/dist/systemvm-templates/";
    private static final String ABSOLUTE_TEMPLATE_PATH = "/usr/share/cloudstack-management/templates/";
    private static final String TEMPLATES_PATH = fetchTemplatesPath();
    private static final String METADATA_FILE_NAME = "metadata.ini";
    private static final String METADATA_FILE = TEMPLATES_PATH + METADATA_FILE_NAME;
    private static final String TEMPORARY_SECONDARY_STORE = "/tmp/tmpSecStorage";
    private static final String PARENT_TEMPLATE_FOLDER = TEMPORARY_SECONDARY_STORE;
    private static final String PARTIAL_TEMPLATE_FOLDER = String.format("/template/tmpl/%d/", Account.ACCOUNT_ID_SYSTEM);
    private static final Integer SCRIPT_TIMEOUT = 1800000;
    private static final Integer LOCK_WAIT_TIMEOUT = 1200;
    public static String CS_MAJOR_VERSION = "4.16";
    public static String CS_TINY_VERSION = "0";

    @Inject
    DataCenterDao dataCenterDao;
    @Inject
    VMTemplateDao vmTemplateDao;
    @Inject
    TemplateDataStoreDao templateDataStoreDao;
    @Inject
    VMInstanceDao vmInstanceDao;
    @Inject
    ImageStoreDao imageStoreDao;
    @Inject
    ClusterDao clusterDao;
    @Inject
    ConfigurationDao configurationDao;

    public SystemVmTemplateRegistration() {
        System.out.println("-------> SystemVmTemplateRegistration created");
        dataCenterDao = new DataCenterDaoImpl();
        vmTemplateDao = new VMTemplateDaoImpl();
        templateDataStoreDao = new BasicTemplateDataStoreDaoImpl();
        vmInstanceDao = new VMInstanceDaoImpl();
        imageStoreDao = new ImageStoreDaoImpl();
        clusterDao = new ClusterDaoImpl();
        configurationDao = new ConfigurationDaoImpl();
    }

    private static class SystemVMTemplateDetails {
        Long id;
        String uuid;
        String name;
        String uniqueName;
        Date created;
        String url;
        String checksum;
        ImageFormat format;
        Integer guestOsId;
        Hypervisor.HypervisorType hypervisorType;
        Long storeId;
        Long size;
        Long physicalSize;
        String installPath;
        boolean deployAsIs;
        Date updated;

        SystemVMTemplateDetails() {
        }

        SystemVMTemplateDetails(String uuid, String name, Date created, String url, String checksum,
                                ImageFormat format, Integer guestOsId, Hypervisor.HypervisorType hypervisorType,
                                Long storeId) {
            this.uuid = uuid;
            this.name = name;
            this.created = created;
            this.url = url;
            this.checksum = checksum;
            this.format = format;
            this.guestOsId = guestOsId;
            this.hypervisorType = hypervisorType;
            this.storeId = storeId;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }

        public String getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public Date getCreated() {
            return created;
        }

        public String getUrl() {
            return url;
        }

        public String getChecksum() {
            return checksum;
        }

        public ImageFormat getFormat() {
            return format;
        }

        public Integer getGuestOsId() {
            return guestOsId;
        }

        public Hypervisor.HypervisorType getHypervisorType() {
            return hypervisorType;
        }

        public Long getStoreId() {
            return storeId;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public Long getPhysicalSize() {
            return physicalSize;
        }

        public void setPhysicalSize(Long physicalSize) {
            this.physicalSize = physicalSize;
        }

        public String getInstallPath() {
            return installPath;
        }

        public void setInstallPath(String installPath) {
            this.installPath = installPath;
        }

        public String getUniqueName() {
            return uniqueName;
        }

        public void setUniqueName(String uniqueName) {
            this.uniqueName = uniqueName;
        }

        public boolean isDeployAsIs() {
            return deployAsIs;
        }

        public void setDeployAsIs(boolean deployAsIs) {
            this.deployAsIs = deployAsIs;
        }

        public Date getUpdated() {
            return updated;
        }

        public void setUpdated(Date updated) {
            this.updated = updated;
        }
    }

    public static final List<Hypervisor.HypervisorType> hypervisorList = Arrays.asList(Hypervisor.HypervisorType.KVM,
            Hypervisor.HypervisorType.VMware,
            Hypervisor.HypervisorType.XenServer,
            Hypervisor.HypervisorType.Hyperv,
            Hypervisor.HypervisorType.LXC,
            Hypervisor.HypervisorType.Ovm3
    );

    public static final Map<Hypervisor.HypervisorType, String> NewTemplateNameList = new HashMap<Hypervisor.HypervisorType, String>();
    public static final Map<Hypervisor.HypervisorType, String> fileNames = new HashMap<Hypervisor.HypervisorType, String>();
    public static final Map<Hypervisor.HypervisorType, String> newTemplateUrl = new HashMap<Hypervisor.HypervisorType, String>();
    public static final Map<Hypervisor.HypervisorType, String> newTemplateChecksum = new HashMap<Hypervisor.HypervisorType, String>();

    public static final Map<Hypervisor.HypervisorType, String> routerTemplateConfigurationNames = new HashMap<Hypervisor.HypervisorType, String>() {
        {
            put(Hypervisor.HypervisorType.KVM, "router.template.kvm");
            put(Hypervisor.HypervisorType.VMware, "router.template.vmware");
            put(Hypervisor.HypervisorType.XenServer, "router.template.xenserver");
            put(Hypervisor.HypervisorType.Hyperv, "router.template.hyperv");
            put(Hypervisor.HypervisorType.LXC, "router.template.lxc");
            put(Hypervisor.HypervisorType.Ovm3, "router.template.ovm3");
        }
    };

    public static final Map<Hypervisor.HypervisorType, Integer> hypervisorGuestOsMap = new HashMap<Hypervisor.HypervisorType, Integer>() {
        {
            put(Hypervisor.HypervisorType.KVM, 15);
            put(Hypervisor.HypervisorType.XenServer, 99);
            put(Hypervisor.HypervisorType.VMware, 99);
            put(Hypervisor.HypervisorType.Hyperv, 15);
            put(Hypervisor.HypervisorType.LXC, 15);
            put(Hypervisor.HypervisorType.Ovm3, 183);
        }
    };

    public static final Map<Hypervisor.HypervisorType, ImageFormat> hypervisorImageFormat = new HashMap<Hypervisor.HypervisorType, ImageFormat>() {
        {
            put(Hypervisor.HypervisorType.KVM, ImageFormat.QCOW2);
            put(Hypervisor.HypervisorType.XenServer, ImageFormat.VHD);
            put(Hypervisor.HypervisorType.VMware, ImageFormat.OVA);
            put(Hypervisor.HypervisorType.Hyperv, ImageFormat.VHD);
            put(Hypervisor.HypervisorType.LXC, ImageFormat.QCOW2);
            put(Hypervisor.HypervisorType.Ovm3, ImageFormat.RAW);
        }
    };

    public static boolean validateIfSeeded(String url, String path) {
        try {
            mountStore(url);
            int lastIdx = path.lastIndexOf(File.separator);
            String partialDirPath = path.substring(0, lastIdx);
            String templatePath = TEMPORARY_SECONDARY_STORE + File.separator + partialDirPath;
            File templateProps = new File(templatePath + "/template.properties");
            if (templateProps.exists()) {
                LOGGER.info("SystemVM template already seeded, skipping registration");
                return true;
            }
            LOGGER.info("SystemVM template not seeded");
            return false;
        } catch (Exception e) {
            throw new CloudRuntimeException("Failed to verify if the template is seeded", e);
        } finally {
            unmountStore();
        }
    }

    private static String calculateChecksum(MessageDigest digest, File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] byteArray = new byte[1024];
            int bytesCount = 0;

            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }

            fis.close();
            byte[] bytes = digest.digest();

            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer
                        .toString((aByte & 0xff) + 0x100, 16)
                        .substring(1));
            }
            return sb.toString();
        } catch (IOException e) {
            String errMsg = String.format("Failed to calculate Checksum of template file: %s ", file.getName());
            LOGGER.error(errMsg, e);
            throw new CloudRuntimeException(errMsg, e);
        }
    }

    public long getRegisteredTemplateId(Pair<Hypervisor.HypervisorType, String> hypervisorAndTemplateName) {
        VMTemplateVO template = vmTemplateDao.findByTemplateName(hypervisorAndTemplateName.second());
        return template != null ? template.getId() : -1;
    }

    private static String fetchTemplatesPath() {
            String filePath = RELATIVE_TEMPLATE_PATH + METADATA_FILE_NAME;
            LOGGER.debug(String.format("Looking for file [ %s ] in the classpath.", filePath));
            File metaFile = new File(filePath);
            String templatePath = null;
            if (metaFile.exists()) {
                templatePath = RELATIVE_TEMPLATE_PATH;
            }
            if (templatePath == null) {
                filePath = ABSOLUTE_TEMPLATE_PATH + METADATA_FILE_NAME;
                metaFile = new File(filePath);
                templatePath = ABSOLUTE_TEMPLATE_PATH;
                LOGGER.debug(String.format("Looking for file [ %s ] in the classpath.", filePath));
                if (!metaFile.exists()) {
                    String errMsg = String.format("Unable to locate metadata file in your setup at %s", filePath.toString());
                    LOGGER.error(errMsg);
                    throw new CloudRuntimeException(errMsg);
                }
            }
        return templatePath;
    }

    private static String getHypervisorName(String name) {
        if (name.equals("xenserver")) {
            return "xen";
        }
        if (name.equals("ovm3")) {
            return "ovm";
        }
        return name;

    }

    private static Hypervisor.HypervisorType getHypervisorType(String hypervisor) {
        if (hypervisor.equalsIgnoreCase("xen")) {
            hypervisor = "xenserver";
        } else if (hypervisor.equalsIgnoreCase("ovm")) {
            hypervisor = "ovm3";
        }
        return Hypervisor.HypervisorType.getType(hypervisor);
    }

    private List<Long> getEligibleZoneIds() {
        List<Long> zoneIds = new ArrayList<>();
        List<ImageStoreVO> stores = imageStoreDao.findByProtocol("nfs");
        for (ImageStoreVO store : stores) {
            if (!zoneIds.contains(store.getDataCenterId())) {
                zoneIds.add(store.getDataCenterId());
            }
        }
        return zoneIds;
    }

    private Pair<String, Long> getNfsStoreInZone(Long zoneId) {
        String url = null;
        Long storeId = null;
        ImageStoreVO store = imageStoreDao.findOneByZoneAndProtocol(zoneId, "nfs");
        if (store != null) {
            url = store.getUrl();
            storeId = store.getId();
        }
        if (url == null) {
            throw new CloudRuntimeException(String.format("Failed to get an NFS store in zone: %s", zoneId));
        }
        return new Pair<>(url, storeId);
    }

    public static void mountStore(String storeUrl) {
        try {
            if (storeUrl != null) {
                URI uri = new URI(UriUtils.encodeURIComponent(storeUrl));
                String host = uri.getHost();
                String mountPath = uri.getPath();
                Script.runSimpleBashScript("mkdir -p " + TEMPORARY_SECONDARY_STORE);
                String mount = String.format(MOUNT_COMMAND, host + ":" + mountPath, TEMPORARY_SECONDARY_STORE);
                Script.runSimpleBashScript(mount);
            }
        } catch (Exception e) {
            String msg = "NFS Store URL is not in the correct format";
            LOGGER.error(msg, e);
            throw new CloudRuntimeException(msg, e);

        }
    }

    private List<String> fetchAllHypervisors(Long zoneId) {
        List<String> hypervisors = new ArrayList<>();
        List<ClusterVO> clusters = clusterDao.listByZoneId(zoneId);
        for (ClusterVO cluster : clusters ) {
            if (!hypervisors.contains(cluster.getHypervisorType().toString())) {
                hypervisors.add(cluster.getHypervisorType().toString());
            }
        }
        return hypervisors;
    }

    private Long createTemplateObjectInDB(SystemVMTemplateDetails details) {
        VMTemplateVO template = new VMTemplateVO();
        template.setUuid(details.getUuid());
        template.setUniqueName(details.getUuid());
        template.setName(details.getName());
        template.setPublicTemplate(false);
        template.setFeatured(false);
        template.setTemplateType(Storage.TemplateType.SYSTEM);
        template.setRequiresHvm(true);
        template.setBits(64);
        template.setAccountId(Account.ACCOUNT_ID_SYSTEM);
        template.setUrl(details.getUrl());
        template.setChecksum(details.getChecksum());
        template.setEnablePassword(false);
        template.setDisplayText(details.getName());
        template.setFormat(details.getFormat());
        template.setGuestOSId(details.getGuestOsId());
        template.setCrossZones(true);
        template.setHypervisorType(details.getHypervisorType());
        template.setState(VirtualMachineTemplate.State.Inactive);
        template.setDeployAsIs(Hypervisor.HypervisorType.VMware.equals(details.getHypervisorType()));
        template = vmTemplateDao.persist(template);
        if (template == null) {
            return null;
        }
        return template.getId();
    }

    private void createTemplateStoreRefEntry(SystemVMTemplateDetails details) {
        TemplateDataStoreVO templateDataStoreVO = new TemplateDataStoreVO(details.storeId, details.getId(), details.getCreated(), 0,
                VMTemplateStorageResourceAssoc.Status.NOT_DOWNLOADED, null, null, null, details.getInstallPath(),details.getUrl());
        templateDataStoreVO.setDataStoreRole(DataStoreRole.Image);
        templateDataStoreDao.persist(templateDataStoreVO);
    }

    public void updateDb(SystemVMTemplateDetails details, boolean updateTemplateDetails) {
        VMTemplateVO template = vmTemplateDao.findById(details.getId());
        if (updateTemplateDetails) {
            template.setSize(details.getSize());
            template.setState(VirtualMachineTemplate.State.Active);
            vmTemplateDao.update(template.getId(), template);
        }
        TemplateDataStoreVO templateDataStoreVO = templateDataStoreDao.findByTemplate(template.getId(), DataStoreRole.Image);
        templateDataStoreVO.setSize(details.getSize());
        templateDataStoreVO.setPhysicalSize(details.getPhysicalSize());
        templateDataStoreVO.setDownloadPercent(100);
        templateDataStoreVO.setDownloadState(VMTemplateStorageResourceAssoc.Status.DOWNLOADED);
        templateDataStoreVO.setLastUpdated(details.getUpdated());
        templateDataStoreDao.update(templateDataStoreVO.getId(), templateDataStoreVO);
    }

    public void updateSystemVMEntries(Long templateId, Hypervisor.HypervisorType hypervisorType) {
        vmInstanceDao.updateSystemVmTemplateId(templateId, hypervisorType);
    }

    public void updateConfigurationParams(Map<String, String> configParams) {
        for (Map.Entry<String, String> config : configParams.entrySet()) {
            configurationDao.update(config.getKey(), config.getValue());
        }
    }

    private static void readTemplateProperties(String path, SystemVMTemplateDetails details) {
        File tmpFile = new File(path);
        Long size = null;
        Long physicalSize = 0L;
        try (FileReader fr = new FileReader(tmpFile); BufferedReader brf = new BufferedReader(fr);) {
            String line = null;
            while ((line = brf.readLine()) != null) {
                if (line.startsWith("size=")) {
                    physicalSize = Long.parseLong(line.split("=")[1]);
                } else if (line.startsWith("virtualsize=")) {
                    size = Long.parseLong(line.split("=")[1]);
                }
                if (size == null) {
                    size = physicalSize;
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to read from template.properties", ex);
        }
        details.setSize(size);
        details.setPhysicalSize(physicalSize);
    }

    private void updateTemplateTablesOnFailure(long templateId) {
        VMTemplateVO template = vmTemplateDao.createForUpdate(templateId);
        template.setState(VirtualMachineTemplate.State.Inactive);
        vmTemplateDao.update(template.getId(), template);
        vmTemplateDao.remove(templateId);
        TemplateDataStoreVO templateDataStoreVO = templateDataStoreDao.findByTemplate(template.getId(), DataStoreRole.Image);
        templateDataStoreDao.remove(templateDataStoreVO.getId());
    }

    public static void unmountStore() {
        try {
            LOGGER.info("Unmounting store");
            String umountCmd = String.format(UMOUNT_COMMAND, TEMPORARY_SECONDARY_STORE);
            Script.runSimpleBashScript(umountCmd);
        } catch (Exception e) {
            String msg = String.format("Failed to unmount store mounted at %s", TEMPORARY_SECONDARY_STORE);
            LOGGER.error(msg, e);
            throw new CloudRuntimeException(msg, e);
        }
    }

    private static void setupTemplate(String templateName, Pair<Hypervisor.HypervisorType, String> hypervisorAndTemplateName,
        String destTempFolder) throws CloudRuntimeException{
        String storageScriptsDir = "scripts/storage/secondary";
        String setupTmpltScript = Script.findScript(storageScriptsDir, "setup-sysvm-tmplt");
        if (setupTmpltScript == null) {
            throw new CloudRuntimeException("Unable to find the createtmplt.sh");
        }
        Script scr = new Script(setupTmpltScript, SCRIPT_TIMEOUT, LOGGER);
        scr.add("-u", templateName);
        scr.add("-f", TEMPLATES_PATH + fileNames.get(hypervisorAndTemplateName.first()));
        scr.add("-h", hypervisorAndTemplateName.first().name().toLowerCase(Locale.ROOT));
        scr.add("-d", destTempFolder);
        String result = scr.execute();
        if (result != null) {
            String errMsg = String.format("failed to create template: %s ", result);
            LOGGER.error(errMsg);
            throw new CloudRuntimeException(errMsg);
        }

    }

    public void registerTemplate(Pair<Hypervisor.HypervisorType, String> hypervisorAndTemplateName,
                                        Pair<String, Long> storeUrlAndId, VMTemplateVO templateVO) {
        Long templateId = null;
        try {
            Hypervisor.HypervisorType hypervisor = hypervisorAndTemplateName.first();
            final String templateName = UUID.randomUUID().toString();
            Date created = new Date(DateUtil.currentGMTTime().getTime());
            SystemVMTemplateDetails details = new SystemVMTemplateDetails(templateName, hypervisorAndTemplateName.second(), created,
                    templateVO.getUrl(), templateVO.getChecksum(), templateVO.getFormat(), (int) templateVO.getGuestOSId(), templateVO.getHypervisorType(),
                    storeUrlAndId.second());
            templateId = templateVO.getId();
            details.setId(templateId);
            String destTempFolderName = String.valueOf(templateId);
            String destTempFolder = PARENT_TEMPLATE_FOLDER + PARTIAL_TEMPLATE_FOLDER + destTempFolderName;
            details.setInstallPath(PARTIAL_TEMPLATE_FOLDER + destTempFolderName + File.separator + templateName + "." + hypervisorImageFormat.get(hypervisor).getFileExtension());
            createTemplateStoreRefEntry(details);
            setupTemplate(templateName, hypervisorAndTemplateName, destTempFolder);
            details.setInstallPath(PARTIAL_TEMPLATE_FOLDER + destTempFolderName + File.separator + templateName + "." + hypervisorImageFormat.get(hypervisor).getFileExtension());
            readTemplateProperties(destTempFolder + "/template.properties", details);
            details.setUpdated(new Date(DateUtil.currentGMTTime().getTime()));
            updateDb(details, false);
        } catch (Exception e) {
            String errMsg = String.format("Failed to register template for hypervisor: %s", hypervisorAndTemplateName.first());
            LOGGER.error(errMsg, e);
            if (templateId != null) {
                updateTemplateTablesOnFailure(templateId);
                cleanupStore(templateId);
            }
            throw new CloudRuntimeException(errMsg, e);
        }

    }
    public void registerTemplate(Connection conn, Pair<Hypervisor.HypervisorType, String> hypervisorAndTemplateName, Pair<String, Long> storeUrlAndId) {
        Long templateId = null;
        try {
            Hypervisor.HypervisorType hypervisor = hypervisorAndTemplateName.first();
            final String templateName = UUID.randomUUID().toString();
            Date created = new Date(DateUtil.currentGMTTime().getTime());
            SystemVMTemplateDetails details = new SystemVMTemplateDetails(templateName, hypervisorAndTemplateName.second(), created,
                    newTemplateUrl.get(hypervisor), newTemplateChecksum.get(hypervisor), hypervisorImageFormat.get(hypervisor), hypervisorGuestOsMap.get(hypervisor), hypervisor, storeUrlAndId.second());
            templateId = createTemplateObjectInDB(details);
            if (templateId == null) {
                throw new CloudRuntimeException(String.format("Failed to register template for hypervisor: %s", hypervisor.name()));
            }
            details.setId(templateId);
            String destTempFolderName = String.valueOf(templateId);
            String destTempFolder = PARENT_TEMPLATE_FOLDER + PARTIAL_TEMPLATE_FOLDER + destTempFolderName;
            details.setInstallPath(PARTIAL_TEMPLATE_FOLDER + destTempFolderName + File.separator + templateName + "." + hypervisorImageFormat.get(hypervisor).getFileExtension());
            createTemplateStoreRefEntry(details);
            setupTemplate(templateName, hypervisorAndTemplateName, destTempFolder);
            details.setInstallPath(PARTIAL_TEMPLATE_FOLDER + destTempFolderName + File.separator + templateName + "." + hypervisorImageFormat.get(hypervisor).getFileExtension());
            readTemplateProperties(destTempFolder + "/template.properties", details);
            details.setUpdated(new Date(DateUtil.currentGMTTime().getTime()));
            updateDb(details, true);
            Map<String, String> configParams = new HashMap<>();
            configParams.put(SystemVmTemplateRegistration.routerTemplateConfigurationNames.get(hypervisorAndTemplateName.first()), hypervisorAndTemplateName.second());
            configParams.put("minreq.sysvmtemplate.version", CS_MAJOR_VERSION + "." + CS_TINY_VERSION);
            updateConfigurationParams(configParams);
            vmInstanceDao.updateSystemVmTemplateId(templateId, hypervisorAndTemplateName.first());
        } catch (Exception e) {
            String errMsg = String.format("Failed to register template for hypervisor: %s", hypervisorAndTemplateName.first());
            LOGGER.error(errMsg, e);
            if (templateId != null) {
                updateTemplateTablesOnFailure(templateId);
                cleanupStore(templateId);
            }
            throw new CloudRuntimeException(errMsg, e);
        }
    }

    public static void parseMetadataFile() {
        try {
            Ini ini = new Ini();
            ini.load(new FileReader(METADATA_FILE));
            for (Hypervisor.HypervisorType hypervisorType : hypervisorList) {
                String hypervisor = hypervisorType.name().toLowerCase(Locale.ROOT);
                Ini.Section section = ini.get(hypervisor);
                NewTemplateNameList.put(hypervisorType, section.get("templatename"));
                fileNames.put(hypervisorType, section.get("filename"));
                newTemplateChecksum.put(hypervisorType, section.get("checksum"));
                newTemplateUrl.put(hypervisorType, section.get("downloadurl"));
            }
        } catch (Exception e) {
            String errMsg = String.format("Failed to parse systemVM template metadata file: %s", METADATA_FILE);
            LOGGER.error(errMsg, e);
            throw new CloudRuntimeException(errMsg, e);
        }
    }

    private static void cleanupStore(Long templateId) {
        String destTempFolder = PARENT_TEMPLATE_FOLDER + PARTIAL_TEMPLATE_FOLDER + String.valueOf(templateId);
        Script.runSimpleBashScript("rm -rf " + destTempFolder);
    }

    public void registerTemplates(Connection conn, Set<Hypervisor.HypervisorType> hypervisorsInUse) {
        GlobalLock lock = GlobalLock.getInternLock("UpgradeDatabase-Lock");
        try {
            LOGGER.info("Grabbing lock to register templates.");
            if (!lock.lock(LOCK_WAIT_TIMEOUT)) {
                throw new CloudRuntimeException("Unable to acquire lock to register SystemVM template.");
            }
            // Check if templates path exists
            try {
                Set<String> hypervisors = hypervisorsInUse.stream().map(Enum::name).
                        map(name -> name.toLowerCase(Locale.ROOT)).map(SystemVmTemplateRegistration::getHypervisorName).collect(Collectors.toSet());
                List<String> templates = new ArrayList<>();
                for (Hypervisor.HypervisorType hypervisorType : hypervisorsInUse) {
                    templates.add(fileNames.get(hypervisorType));
                }

                boolean templatesFound = true;
                for (String hypervisor : hypervisors) {
                    String matchedTemplate = templates.stream().filter(x -> x.contains(hypervisor)).findAny().orElse(null);
                    if (matchedTemplate == null) {
                        templatesFound = false;
                        break;
                    }
                    MessageDigest mdigest = MessageDigest.getInstance(HASH_ALGORITHM);
                    File tempFile = new File(TEMPLATES_PATH + matchedTemplate);
                    String templateChecksum = calculateChecksum(mdigest, tempFile);
                    if (!templateChecksum.equals(newTemplateChecksum.get(getHypervisorType(hypervisor)))) {
                        LOGGER.error(String.format("Checksum mismatch: %s != %s ", templateChecksum, newTemplateChecksum.get(getHypervisorType(hypervisor))));
                        templatesFound = false;
                        break;
                    }
                }

                if (!templatesFound) {
                    String errMsg = "SystemVm template not found. Cannot upgrade system Vms";
                    LOGGER.error(errMsg);
                    throw new CloudRuntimeException(errMsg);
                }

                // Perform Registration if templates not already registered
                List<Long> zoneIds = getEligibleZoneIds();
                for (Long zoneId : zoneIds) {
                    Pair<String, Long> storeUrlAndId = getNfsStoreInZone(zoneId);
                    mountStore(storeUrlAndId.first());
                    List<String> hypervisorList = fetchAllHypervisors(zoneId);
                    for (String hypervisor : hypervisorList) {
                        Hypervisor.HypervisorType name = Hypervisor.HypervisorType.getType(hypervisor);
                        String templateName = NewTemplateNameList.get(name);
                        Pair<Hypervisor.HypervisorType, String> hypervisorAndTemplateName = new Pair<>(name, templateName);
                        long templateId = getRegisteredTemplateId(hypervisorAndTemplateName);
                        if (templateId != -1) {
                            continue;
                        }
                        registerTemplate(conn, hypervisorAndTemplateName, storeUrlAndId);
                    }
                    unmountStore();
                }
            } catch (Exception e) {
                unmountStore();
                throw new CloudRuntimeException("Failed to register systemVM template. Upgrade Failed");
            }
        } finally {
            lock.unlock();
            lock.releaseRef();
        }
    }

    public int listZonesCount() {
        return dataCenterDao.listAll().size();
    }
}
