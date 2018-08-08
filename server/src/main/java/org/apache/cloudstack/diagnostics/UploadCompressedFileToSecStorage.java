package org.apache.cloudstack.diagnostics;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import org.apache.log4j.Logger;

public class UploadCompressedFileToSecStorage implements CompressedFileUploader {
    public static final Logger s_logger = Logger.getLogger(UploadCompressedFileToSecStorage.class.getName());
        public CompressedFileUploader.Status status = CompressedFileUploader.Status.NOT_STARTED;
        public String errorString = "";
        public long totalBytes = 0;
        public long entitySizeinBytes;
        private String sourcePath;
        private String ftpUrl;
        private UploadCompleteCallback completionCallback;
        private BufferedInputStream inputStream = null;
        private BufferedOutputStream outputStream = null;
        private static final int CHUNK_SIZE = 1024 * 1024; //1M

        public UploadCompressedFileToSecStorage(String sourcePath, String url, UploadCompleteCallback callback, long entitySizeinBytes) {

            this.sourcePath = sourcePath;
            ftpUrl = url;
            completionCallback = callback;
            this.entitySizeinBytes = entitySizeinBytes;

        }

        @Override
        public String upload(UploadCompleteCallback callback) {

            switch (status) {
                case ABORTED:
                case UNRECOVERABLE_ERROR:
                case UPLOAD_FINISHED:
                    return null;
                default:

            }
            StringBuffer sb = new StringBuffer(ftpUrl);
            sb.append(";type=i");

            try {
                URL url = new URL(sb.toString());
                URLConnection urlc = url.openConnection();
                File sourceFile = new File(sourcePath);
                entitySizeinBytes = sourceFile.length();

                outputStream = new BufferedOutputStream(urlc.getOutputStream());
                inputStream = new BufferedInputStream(new FileInputStream(sourceFile));

                status = UploadCompressedFileToSecStorage.Status.IN_PROGRESS;

                int bytes = 0;
                byte[] block = new byte[CHUNK_SIZE];
                boolean done = false;
                while (!done && status != Status.ABORTED) {
                    if ((bytes = inputStream.read(block, 0, CHUNK_SIZE)) > -1) {
                        outputStream.write(block, 0, bytes);
                        totalBytes += bytes;
                    } else {
                        done = true;
                    }
                }
                status = UploadCompressedFileToSecStorage.Status.UPLOAD_FINISHED;
                return ftpUrl;
            } catch (MalformedURLException e) {
                status = UploadCompressedFileToSecStorage.Status.UNRECOVERABLE_ERROR;
                errorString = e.getMessage();
                s_logger.error(errorString);
            } catch (IOException e) {
                status = UploadCompressedFileToSecStorage.Status.UNRECOVERABLE_ERROR;
                errorString = e.getMessage();
                s_logger.error(errorString);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (IOException ioe) {
                    s_logger.error(" Caught exception while closing the resources");
                }
                if (callback != null) {
                    callback.uploadComplete(status);
                }
            }

            return null;
        }

        @Override
        public void setResume(boolean resume) {

        }

        @Override
        public void run() {
            try {
                upload(completionCallback);
            } catch (Throwable t) {
                s_logger.warn("Caught exception during upload " + t.getMessage(), t);
                errorString = "Failed to download: " + t.getMessage();
                status = UploadCompressedFileToSecStorage.Status.UNRECOVERABLE_ERROR;
            }

        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public String getUploadError() {
            return errorString;
        }

        @Override
        public String getUploadLocalPath() {
            return sourcePath;
        }

        @Override
        public void setStatus(Status status) {
            this.status = status;
        }

        @Override
        public void setUploadError(String string) {
            errorString = string;
        }

}
