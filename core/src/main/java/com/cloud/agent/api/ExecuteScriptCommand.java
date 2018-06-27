package com.cloud.agent.api;

import com.cloud.agent.api.routing.NetworkElementCommand;
import org.apache.log4j.Logger;

public class ExecuteScriptCommand extends NetworkElementCommand {
    private static final Logger LOGGER = Logger.getLogger(ExecuteScriptCommand.class);

    private String commandScript;
    private final boolean executeInSequence;

    public ExecuteScriptCommand(String commandScript, boolean executeInSequence) {
        this.commandScript = commandScript;
        this.executeInSequence = executeInSequence;
    }


    @Override
    public boolean executeInSequence()
    {
        return this.executeInSequence;
    }

    @Override
    public boolean isQuery() {

        return true;
    }

    public String getCommandScript() {
        return commandScript;
    }

}
