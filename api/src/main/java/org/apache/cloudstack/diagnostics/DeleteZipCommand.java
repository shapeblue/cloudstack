package org.apache.cloudstack.diagnostics;

import com.cloud.agent.api.Command;
import com.cloud.agent.api.to.DataStoreTO;

public class DeleteZipCommand extends Command {
    private String zipFile;
    private DataStoreTO destStore;

    public DeleteZipCommand(String zipFile, DataStoreTO destStore) {
        this.zipFile = zipFile;
        this.destStore = destStore;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public DataStoreTO getDestStore() {
        return destStore;
    }

    public String getZipFile() {
        return zipFile;
    }

}
