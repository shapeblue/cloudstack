package org.apache.cloudstack.storage.configdrive.org.apache.cloudstack.storage.diagnostics;

import com.cloud.utils.StringUtils;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.joda.time.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DiagnosticsBuilder {
    public static final Logger LOG = Logger.getLogger(DiagnosticsBuilder.class);

    static void writeFile(File folder, String file, String content) {
        try {
            FileUtils.write(new File(folder, file), content, com.cloud.utils.StringUtils.getPreferredCharset(), false);
        } catch (IOException ex) {
            throw new CloudRuntimeException("Failed to create diagnostics drive file " + file, ex);
        }
    }

    public static String buildConfigDrive(String folder, String diagnosticsFileName) {
        if (diagnosticsFileName == null) {
            throw new CloudRuntimeException("No zip file provided");
        }
        byte[] decoded = Base64.decodeBase64(diagnosticsFileName.getBytes(StandardCharsets.US_ASCII));
        Path destPath = Paths.get(folder, diagnosticsFileName);
        File typeFolder = new File(Diagnostics.DIAGNOSTICSDIR);
        if (!typeFolder.exists() && !typeFolder.mkdirs()) {
            throw new CloudRuntimeException("Failed to create folder: " + typeFolder);
        }
        try {
            Path tempDir = Files.createTempDirectory(Diagnostics.DIAGNOSTICSDIR);
            Files.createDirectories(destPath.getParent());
            File zipFile = Files.write(destPath, decoded).toFile();
            linkUserData(destPath.toString());
            return zipFile.toString();

        } catch (IOException e) {
            throw new CloudRuntimeException("Failed due to", e);
        }
    }

    public static File base64StringToFile(String encodedIsoData, String folder, String fileName) throws IOException {
        byte[] decoded = Base64.decodeBase64(encodedIsoData.getBytes(StandardCharsets.US_ASCII));
        Path destPath = Paths.get(folder, fileName);
        try {
            Files.createDirectories(destPath.getParent());
        } catch (final IOException e) {
            LOG.warn("Exception hit while trying to recreate directory: " + destPath.getParent().toString());
        }
        return Files.write(destPath, decoded).toFile();
    }

    private static void deleteTempDir(Path tempDir) {
        try {
            if (tempDir != null) {
                FileUtils.deleteDirectory(tempDir.toFile());
            }
        } catch (IOException ioe) {
            LOG.warn("Failed to delete Diagnostics temporary directory: " + tempDir.toString(), ioe);
        }
    }

    static void linkUserData(String tempDirName) {
        String userDataFilePath = tempDirName + Diagnostics.cloudStackConfigDriveName + "userdata/user_data.txt";
        File file = new File(userDataFilePath);
        if (file.exists()) {
            Script hardLink = new Script("ln", Duration.standardSeconds(300), LOG);
            hardLink.add(userDataFilePath);
            hardLink.add(tempDirName + Diagnostics.DIAGNOSTICSDIR);
            LOG.debug("execute command: " + hardLink.toString());

            String executionResult = hardLink.execute();
            if (StringUtils.isNotBlank(executionResult)) {
                throw new CloudRuntimeException("Unable to create user_data link due to " + executionResult);
            }
        }
    }

}
