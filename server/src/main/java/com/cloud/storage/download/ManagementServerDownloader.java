package com.cloud.storage.download;

import com.cloud.storage.JavaStorageLayer;
import com.cloud.storage.StorageLayer;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ManagementServerDownloader {
    private static final Logger s_logger = Logger.getLogger(ManagementServerDownloader.class);
    private StorageLayer _storage;
    private int _timeout;
    private final Random _rand = new Random(System.currentTimeMillis());
    private final Map<String, String> _storageMounts = new HashMap<>();

    public static final ConfigKey<String> MOUNT_PARENT = new ConfigKey<>("Advanced", String.class,
            "mount.parent", "/var/cloudstack/mnt",
            "The mount point on the Management Server for Secondary Storage.",
            true, ConfigKey.Scope.Global);

    public ManagementServerDownloader() {
        this._storage = new JavaStorageLayer();
        _timeout = 1440 * 1000;
    }

    public String getMountPoint(String storageUrl, Integer nfsVersion) {
        String mountPoint = null;
        synchronized (_storageMounts) {
            mountPoint = _storageMounts.get(storageUrl);
            if (mountPoint != null) {
                return mountPoint;
            }

            URI uri;
            try {
                uri = new URI(storageUrl);
            } catch (URISyntaxException e) {
                s_logger.error("Invalid storage URL format ", e);
                throw new CloudRuntimeException("Unable to create mount point due to invalid storage URL format " + storageUrl);
            }

            mountPoint = mount(uri.getHost() + ":" + uri.getPath(), MOUNT_PARENT.value(), nfsVersion);
            if (mountPoint == null) {
                s_logger.error("Unable to create mount point for " + storageUrl);
                return "/mnt/sec"; // throw new CloudRuntimeException("Unable to create mount point for " + storageUrl);
            }

            _storageMounts.put(storageUrl, mountPoint);
            return mountPoint;
        }
    }

    private String mount(String path, String parent, Integer nfsVersion) {
        String mountPoint = setupMountPoint(parent);
        if (mountPoint == null) {
            s_logger.warn("Unable to create a mount point");
            return null;
        }

        Script script = null;
        String result = null;
        Script command = new Script(true, "mount", _timeout, s_logger);
        command.add("-t", "nfs");
        if (nfsVersion != null){
            command.add("-o", "vers=" + nfsVersion);
        }
        // command.add("-o", "soft,timeo=133,retrans=2147483647,tcp,acdirmax=0,acdirmin=0");
        if ("Mac OS X".equalsIgnoreCase(System.getProperty("os.name"))) {
            command.add("-o", "resvport");
        }
        command.add(path);
        command.add(mountPoint);
        result = command.execute();
        if (result != null) {
            s_logger.warn("Unable to mount " + path + " due to " + result);
            File file = new File(mountPoint);
            if (file.exists()) {
                file.delete();
            }
            return null;
        }

        // Change permissions for the mountpoint
        script = new Script(true, "chmod", _timeout, s_logger);
        script.add("1777", mountPoint);
        result = script.execute();
        if (result != null) {
            s_logger.warn("Unable to set permissions for " + mountPoint + " due to " + result);
        }
        return mountPoint;
    }

    private String setupMountPoint(String parent) {
        String mountPoint = null;
        long mshostId = ManagementServerNode.getManagementServerId();
        for (int i = 0; i < 10; i++) {
            String mntPt = parent + File.separator + String.valueOf(mshostId) + "." + Integer.toHexString(_rand.nextInt(Integer.MAX_VALUE));
            File file = new File(mntPt);
            if (!file.exists()) {
                if (_storage.mkdir(mntPt)) {
                    mountPoint = mntPt;
                    break;
                }
            }
            s_logger.error("Unable to create mount: " + mntPt);
        }

        return mountPoint;
    }
    protected boolean mountExists(String localRootPath, URI uri) {
        Script script = null;
        script = new Script(true, "mount", _timeout, s_logger);

        List<String> res = new ArrayList<String>();
        ZfsPathParser parser = new ZfsPathParser(localRootPath);
        script.execute(parser);
        res.addAll(parser.getPaths());
        for (String s : res) {
            if (s.contains(localRootPath)) {
                s_logger.debug("Some device already mounted at " + localRootPath + ", no need to mount " + uri.toString());
                return true;
            }
        }
        return false;
    }
    public static class ZfsPathParser extends OutputInterpreter {
        String _parent;
        List<String> paths = new ArrayList<String>();

        public ZfsPathParser(String parent) {
            _parent = parent;
        }

        @Override
        public String interpret(BufferedReader reader) throws IOException {
            String line = null;
            while ((line = reader.readLine()) != null) {
                paths.add(line);
            }
            return null;
        }

        public List<String> getPaths() {
            return paths;
        }

        @Override
        public boolean drain() {
            return true;
        }
    }
}
