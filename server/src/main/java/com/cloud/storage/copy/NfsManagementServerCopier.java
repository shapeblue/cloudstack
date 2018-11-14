package com.cloud.storage.copy;

import com.cloud.storage.ImageStoreDetailsUtil;
import com.cloud.storage.mount.MountManager;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateInfo;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Component
public class NfsManagementServerCopier implements ManagementServerCopier {
    private static final Logger s_logger = Logger.getLogger(NfsManagementServerCopier.class);

    @Inject
    private MountManager mountManager;
    @Inject
    private ImageStoreDetailsUtil imageStoreDetailsUtil;
    @Inject
    private TemplateDataStoreDao vmTemplateStoreDao;


    public boolean copy(TemplateInfo srcTemplate, DataStore destStore) {
        Integer nfsVersion = imageStoreDetailsUtil.getNfsVersion(srcTemplate.getDataStore().getId());
        String sourceMountPoint = mountManager.getMountPoint(srcTemplate.getDataStore().getUri(), nfsVersion);
        String destMountPoint = mountManager.getMountPoint(destStore.getUri(), nfsVersion);
        String installPath  = getTemplateDataStore(srcTemplate).getInstallPath();
        createFolder(Paths.get(destMountPoint, installPath).getParent());
        String sourcePath = Paths.get(sourceMountPoint, installPath).getParent().toString();
        String destinationPath = Paths.get(destMountPoint, installPath).getParent().toString();
        return copyFolderContent(sourcePath, destinationPath);
    }

    private boolean copyFolderContent(String sourcePath, String destinationPath) {
        String command = String.format("cp %s%s* %s", sourcePath, File.separator, destinationPath);
        String result = Script.runSimpleBashScript(command);
        if (result != null) {
            s_logger.warn(String.format("Unable to copy from %s to %s due to %s.%n", sourcePath, destinationPath, result));
            return false;
        }
        return true;
    }

    /**
     * Create folder on path if it does not exist
     */
    public static void createFolder(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            s_logger.error(String.format("Unable to create directory from path %s: %s.%n", path, e.toString()));
            throw new CloudRuntimeException(e);
        }
    }

    private TemplateDataStoreVO getTemplateDataStore(TemplateInfo template) {
        return Optional.ofNullable(vmTemplateStoreDao.findByStoreTemplate(template.getDataStore().getId(), template.getId()))
                .orElseThrow(() -> new CloudRuntimeException(String.format("Unable to find template store ref by template id %d.%n", template.getId())));
    }
}
