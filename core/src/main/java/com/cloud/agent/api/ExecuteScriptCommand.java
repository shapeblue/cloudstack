package com.cloud.agent.api;

import com.cloud.agent.api.routing.NetworkElementCommand;
import org.apache.log4j.Logger;

public class ExecuteScriptCommand extends NetworkElementCommand {
    private static final Logger LOGGER = Logger.getLogger(ExecuteScriptCommand.class);

    @Override
    public boolean executeInSequence()
    {
        return false;
    }

    @Override
    public boolean isQuery() {

        return true;
    }

}
