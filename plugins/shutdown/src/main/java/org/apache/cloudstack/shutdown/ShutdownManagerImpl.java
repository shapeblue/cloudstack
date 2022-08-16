package org.apache.cloudstack.shutdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.cloudstack.api.command.ReadyForShutdownCmd;

import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;

public class ShutdownManagerImpl extends ManagerBase implements ShutdownManager, PluggableService{

    protected ShutdownManagerImpl() {
        super();
    }

    @Override
    public Boolean isReadyForShutdown() {
        Random random = new Random();
        return random.nextBoolean();
    }


    @Override
    public List<Class<?>> getCommands() {
        final List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(ReadyForShutdownCmd.class);
        return cmdList;
    }
}
