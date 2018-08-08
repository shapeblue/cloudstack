package org.apache.cloudstack.diagnostics;

public interface CompressedFileUploader extends Runnable {

    public interface UploadCompleteCallback {
        void uploadComplete(Status status);

    }

    public static enum Status {
        UNKNOWN, NOT_STARTED, IN_PROGRESS, ABORTED, UNRECOVERABLE_ERROR, RECOVERABLE_ERROR, UPLOAD_FINISHED, POST_UPLOAD_FINISHED
    }

    public String upload(UploadCompleteCallback callback);

    public CompressedFileUploader.Status getStatus();

    public String getUploadError();

    public String getUploadLocalPath();

    public void setStatus(CompressedFileUploader.Status status);

    public void setUploadError(String string);

    public void setResume(boolean resume);

}
