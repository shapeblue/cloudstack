package com.cloud.agent.api.baremetal;

import com.cloud.agent.api.Command;
import com.cloud.agent.api.to.VirtualMachineTO;

public class DestroyCommand extends Command {

    VirtualMachineTO vm;
    boolean executeInSequence;

    public DestroyCommand(VirtualMachineTO vm, boolean executeInSequence) {
        this.vm = vm;
        this.executeInSequence = executeInSequence;
    }

    @Override
    public boolean executeInSequence() {

        if (vm.getName() != null && vm.getName().startsWith("r-")) {
            return false;
        }
        return executeInSequence;
    }

    public VirtualMachineTO getVm() {
        return vm;
    }
}
