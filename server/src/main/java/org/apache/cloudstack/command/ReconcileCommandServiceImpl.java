// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.Command.State;
import com.cloud.agent.api.MigrateCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.storage.MigrateVolumeCommand;
import com.cloud.agent.api.to.DataObjectType;
import com.cloud.agent.api.to.DataTO;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.exception.CloudRuntimeException;

import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.command.dao.ReconcileCommandDao;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.storage.command.CommandResult;
import org.apache.cloudstack.storage.command.CopyCommand;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.cloudstack.storage.volume.VolumeOnStorageTO;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.springframework.stereotype.Component;

@Component
public class ReconcileCommandServiceImpl extends ManagerBase implements ReconcileCommandService, Configurable {

    final static long ManagementServerId = ManagementServerNode.getManagementServerId();
    final static int MaxReconcileAttempts = 10;
    final static int GracePeriod = 10 * 60;  // 10 minutes
    final static List<Hypervisor.HypervisorType> SupportedHypervisorTypes = Arrays.asList(Hypervisor.HypervisorType.KVM);

    private ScheduledExecutorService reconcileCommandsExecutor;
    private ExecutorService reconcileCommandTaskExecutor;
    CompletionService<ReconcileCommandResult> completionService;

    @Inject
    ReconcileCommandDao reconcileCommandDao;
    @Inject
    ManagementServerHostDao managementServerHostDao;
    @Inject
    HostDao hostDao;
    @Inject
    AgentManager agentManager;
    @Inject
    VMInstanceDao vmInstanceDao;
    @Inject
    VolumeDao volumeDao;

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (ReconcileCommandsEnabled.value()) {
            // create thread pool and blocking queue
            final int workersCount = ReconcileCommandsWorkers.value();
            reconcileCommandTaskExecutor = Executors.newFixedThreadPool(workersCount, new NamedThreadFactory("Reconcile-Command-Task-Executor"));
            final BlockingQueue<Future<ReconcileCommandResult>> queue = new LinkedBlockingQueue<>(workersCount);
            completionService = new ExecutorCompletionService<>(reconcileCommandTaskExecutor, queue);

            reconcileCommandsExecutor = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("Reconcile-Commands-Worker"));
            reconcileCommandsExecutor.scheduleWithFixedDelay(new ReconcileCommandsWorker(),
                    ReconcileCommandsInterval.value(), ReconcileCommandsInterval.value(), TimeUnit.SECONDS);
        }

        return true;
    }

    @Override
    public boolean stop() {
        if (reconcileCommandsExecutor != null) {
            reconcileCommandsExecutor.shutdownNow();
        }
        if (reconcileCommandTaskExecutor != null) {
            reconcileCommandTaskExecutor.shutdownNow();
        }
        if (ReconcileCommandsEnabled.value()) {
            reconcileCommandDao.updateCommandsToInterrupted(ManagementServerId);
        }

        return true;
    }

    @Override
    public String getConfigComponentName() {
        return ReconcileCommandService.class.getName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ ReconcileCommandsEnabled, ReconcileCommandsInterval, ReconcileCommandsWorkers };
    }

    @Override
    public void persistReconcileCommands(Long hostId, Long requestSequence, Command[] commands) {
        if (!ReconcileCommandsEnabled.value()) {
            return;
        }
        HostVO host = hostDao.findById(hostId);
        if (host == null || !SupportedHypervisorTypes.contains(host.getHypervisorType())) {
            return;
        }
        for (Command cmd : commands) {
            if (cmd.isReconcile()) {
                persistReconcileCommand(hostId, requestSequence, cmd);
            }
        }
    }

    private void persistReconcileCommand(Long hostId, Long requestSequence, Command cmd) {
        ReconcileCommandVO reconcileCommandVO = new ReconcileCommandVO();
        reconcileCommandVO.setManagementServerId(ManagementServerId);
        reconcileCommandVO.setCommandInfo(CommandInfo.GSON.toJson(cmd));
        reconcileCommandVO.setCommandName(cmd.toString());
        reconcileCommandVO.setCreated(new Date());
        reconcileCommandVO.setUpdated(new Date());
        reconcileCommandVO.setStateByManagement(State.CREATED);
        reconcileCommandVO.setHostId(hostId);
        reconcileCommandVO.setRequestSequence(requestSequence);
        reconcileCommandDao.persist(reconcileCommandVO);
    }

    @Override
    public void updateReconcileCommand(long requestSeq, Command command, Answer answer, Command.State newStateByManagement, Command.State newStateByAgent) {
        String commandKey = getCommandKey(requestSeq, command);
        logger.debug(String.format("Updating reconcile command %s with answer %s and new states %s-%s", commandKey, answer, newStateByManagement, newStateByAgent));
        ReconcileCommandVO reconcileCommandVO = reconcileCommandDao.findCommand(requestSeq, command.toString());
        if (reconcileCommandVO == null) {
            return;
        }
        boolean updated = false;
        if (newStateByManagement != null) {
            if (!newStateByManagement.equals(reconcileCommandVO.getStateByManagement())) {
                reconcileCommandVO.setStateByManagement(newStateByManagement);
                updated = true;
            }
            if (State.RECONCILE_FAILED.equals(newStateByManagement)) {
                reconcileCommandVO.incrementRetryCount();
                updated = true;
            }
            if (ManagementServerId != ManagementServerNode.getManagementServerId()) {
                reconcileCommandVO.setManagementServerId(ManagementServerId);
                updated = true;
            }
        }
        if (newStateByAgent != null) {
            if (!newStateByAgent.equals(reconcileCommandVO.getStateByAgent())) {
                reconcileCommandVO.setStateByAgent(newStateByAgent);
                updated = true;
            }
        }
        if (answer != null && (reconcileCommandVO.getAnswerName() == null || !reconcileCommandVO.getAnswerName().equals(answer.toString()))) {
            reconcileCommandVO.setAnswerName(answer.toString());
            reconcileCommandVO.setAnswerInfo(CommandInfo.GSON.toJson(answer));
            updated = true;
        }
        if (updated) {
            reconcileCommandVO.setUpdated(new Date());
            reconcileCommandDao.update(reconcileCommandVO.getId(), reconcileCommandVO);
        }
    }

    private String getCommandKey(long requestSeq, Command command) {
        return requestSeq + "-" + command;
    }

    private class ReconcileCommandsWorker extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            GlobalLock gcLock = GlobalLock.getInternLock("Reconcile.Commands.Lock");
            try {
                if (gcLock.lock(3)) {
                    try {
                        reallyRun();
                    } finally {
                        gcLock.unlock();
                    }
                }
            } finally {
                gcLock.releaseRef();
            }
        }

        private List<ReconcileCommandVO> getReconcileCommands() {
            ManagementServerHostVO msHost = managementServerHostDao.findOneInUpState(new Filter(ManagementServerHostVO.class, "id", true, 0L, 1L));
            if (msHost == null || msHost.getMsid() != ManagementServerId) {
                return new ArrayList<>();
            }
            return reconcileCommandDao.listByState(State.INTERRUPTED, State.TIMED_OUT, State.DANGLED_IN_BACKEND);
        }

        public void reallyRun() {
            List<ReconcileCommandVO> reconcileCommands = getReconcileCommands();
            logger.debug(String.format("Reconciling %s command(s) ...", reconcileCommands.size()));
            for (ReconcileCommandVO reconcileCommand : reconcileCommands) {
                ReconcileCommandTask task = new ReconcileCommandTask(reconcileCommand);
                completionService.submit(task);
            }
            for (int i = 0; i < reconcileCommands.size(); i++) {
                try {
                    Future<ReconcileCommandResult> future = completionService.take();
                    ReconcileCommandResult result = future.get();
                    long requestSequence = result.getRequestSequence();
                    Command command = result.getCommand();
                    ReconcileAnswer answer = result.getAnswer();
                    String commandKey = getCommandKey(requestSequence, command);
                    if (result.isFailed()) {
                        throw new CloudRuntimeException(String.format("Failed to reconcile command %s due to: %s", commandKey, result.getResult()));
                    }
                    if (answer != null && answer.getResult()) {
                        logger.debug(String.format("Command %s has been reconciled with answer %s", commandKey, answer));
                        if (result.isReconciled()) {
                            if (processReconcileAnswer(requestSequence, command, answer)) {
                                updateReconcileCommand(requestSequence, result.getCommand(), answer, State.RECONCILED, null);
                                reconcileCommandDao.removeCommand(requestSequence, result.getCommand().toString(), null);
                            } else {
                                updateReconcileCommand(requestSequence, result.getCommand(), answer, State.RECONCILE_READY, null);
                            }
                        } else {
                            updateReconcileCommand(requestSequence, result.getCommand(), answer, State.RECONCILE_FAILED, null);
                        }
                    } else {
                        logger.info(String.format("Command %s is not reconciled, will retry", commandKey));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error(String.format("Failed to reconcile command due to: %s", e.getMessage()), e);
                    throw new CloudRuntimeException("Failed to reconcile command");
                }
            }
        }
    }


    public static class ReconcileCommandResult extends CommandResult {
        long requestSequence;
        Command command;
        ReconcileAnswer answer;
        boolean isReconciled;

        public ReconcileCommandResult(long requestSequence, Command command, ReconcileAnswer answer, boolean isReconciled) {
            super();
            this.requestSequence = requestSequence;
            this.command = command;
            this.answer = answer;
            this.isReconciled = isReconciled;
        }

        public long getRequestSequence() {
            return requestSequence;
        }

        public Command getCommand() {
            return command;
        }

        public ReconcileAnswer getAnswer() {
            return answer;
        }

        public boolean isReconciled() {
            return isReconciled;
        }
    }

    protected class ReconcileCommandTask implements Callable<ReconcileCommandResult> {
        long requestSequence;
        Command command;
        State stateByManagement;
        State stateByAgent;
        Long hostId;
        Long retryCount;
        ReconcileCommandVO reconcileCommand;

        public ReconcileCommandTask(ReconcileCommandVO reconcileCommand) {
            this.requestSequence = reconcileCommand.getRequestSequence();
            this.stateByManagement = reconcileCommand.getStateByManagement();
            this.stateByAgent = reconcileCommand.getStateByAgent();
            this.hostId = reconcileCommand.getHostId();
            this.retryCount = reconcileCommand.getRetryCount();
            this.reconcileCommand = reconcileCommand;
            this.command = ReconcileCommandUtils.parseCommandInfo(reconcileCommand.getCommandName(), reconcileCommand.getCommandInfo());
        }

        @Override
        public ReconcileCommandResult call() {
            String commandKey = getCommandKey(requestSequence, command);
            HostVO host = hostDao.findByIdIncludingRemoved(hostId);
            assert host != null;
            if (!SupportedHypervisorTypes.contains(host.getHypervisorType())) {
                return new ReconcileCommandResult(requestSequence, command, null, false);
            }

            logger.debug(String.format("Reconciling command %s with state %s-%s", commandKey, stateByManagement, stateByAgent));

            if (State.TIMED_OUT.equals(stateByManagement)) {
                logger.debug(String.format("The command %s timed out on management server. Reconciling ...", commandKey));
                return reconcile(reconcileCommand);
            } else if (Arrays.asList(State.INTERRUPTED, State.RECONCILE_READY).contains(stateByManagement)) {
                logger.debug(String.format("The command %s is %s on management server. Reconciling ...", commandKey, stateByManagement));
                return reconcile(reconcileCommand);
            } else if (State.RECONCILING.equals(stateByManagement)) {
                Date now = new Date();
                if (reconcileCommand.getUpdated() != null && reconcileCommand.getUpdated().getTime() > now.getTime() - GracePeriod * 1000) {
                    logger.debug(String.format("The command %s is being reconciled, skipping and wait for next run", commandKey));
                } else {
                    logger.debug(String.format("The command %s is %s, the state seems out of date, updating to RECONCILE_READY", commandKey, stateByManagement));
                    reconcileCommand = reconcileCommandDao.findById(reconcileCommand.getId());
                    reconcileCommand.setStateByManagement(State.RECONCILE_READY);
                    reconcileCommandDao.update(reconcileCommand.getId(), reconcileCommand);
                }
            } else if (State.RECONCILE_FAILED.equals(stateByManagement)) {
                if (retryCount != null && retryCount <= MaxReconcileAttempts) {
                    logger.debug(String.format("The command %s has been reconciled %s times, retrying", commandKey, retryCount));
                    return reconcile(reconcileCommand);
                } else {
                    logger.debug(String.format("The command %s has been reconciled %s times, skipping", commandKey, retryCount));
                }
            } else if (State.RECONCILED.equals(stateByManagement)) {
                logger.debug(String.format("The command %s has been reconciled, skipping", commandKey));
            } else if (stateByAgent == null) {
                logger.debug(String.format("Skipping the reconciliation of command %s, because the state by agent is null", commandKey));
            } else if (Arrays.asList(State.STARTED, State.PROCESSING, State.PROCESSING_IN_BACKEND).contains(stateByAgent)) {
                if (Status.Up.equals(host.getStatus())) {
                    logger.debug(String.format("Skipping the reconciliation of command %s, because the host is Up, the command may be still in processing", commandKey));
                    return new ReconcileCommandResult(requestSequence, command, null, false);
                } else {
                    logger.debug(String.format("The host is disconnected on %s, removed on %s, Reconciling command %s ...", host.getDisconnectedOn(), host.getRemoved(), commandKey));
                    return reconcile(reconcileCommand);
                }
            } else if (Arrays.asList(State.COMPLETED, State.FAILED).contains(stateByAgent)) {
                Date now = new Date();
                if (reconcileCommand.getUpdated() != null && reconcileCommand.getUpdated().getTime() > now.getTime() - GracePeriod * 1000) {
                    logger.debug(String.format("The command %s is %s on host %s, it seems the answer is not processed by any management server. Skipping ...", commandKey, stateByAgent, hostId));
                } else {
                    logger.debug(String.format("The command %s is %s on host %s, it seems the answer is not processed by any management server. Reconciling ...", commandKey, stateByAgent, hostId));
                    return reconcile(reconcileCommand);
                }
            } else if (Arrays.asList(State.INTERRUPTED, State.DANGLED_IN_BACKEND).contains(stateByAgent)) {
                logger.debug(String.format("The command %s is %s on host %s, the cloudstack agent might has been restarted. Reconciling ...", commandKey, stateByAgent, hostId));
                return reconcile(reconcileCommand);
            }

            return new ReconcileCommandResult(requestSequence, command, null, false);
        }
    }

    protected ReconcileCommandResult reconcile(ReconcileCommandVO reconcileCommandVO) {
        Command command = ReconcileCommandUtils.parseCommandInfo(reconcileCommandVO.getCommandName(), reconcileCommandVO.getCommandInfo());

        if (command instanceof MigrateCommand) {
            updateReconcileCommand(reconcileCommandVO.getRequestSequence(), command, null, State.RECONCILING, null);
            return reconcile(reconcileCommandVO, (MigrateCommand) command);
        } else if (command instanceof CopyCommand) {
            updateReconcileCommand(reconcileCommandVO.getRequestSequence(), command, null, State.RECONCILING, null);
            return reconcile(reconcileCommandVO, (CopyCommand) command);
        } else if (command instanceof MigrateVolumeCommand) {
            updateReconcileCommand(reconcileCommandVO.getRequestSequence(), command, null, State.RECONCILING, null);
            return reconcile(reconcileCommandVO, (MigrateVolumeCommand) command);
        } else {
            logger.error(String.format("Unsupported reconcile command %s ", command));
        }

        return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, true);
    }

    private ReconcileCommandResult reconcile(ReconcileCommandVO reconcileCommandVO, MigrateCommand command) {
        ReconcileMigrateAnswer reconcileMigrateAnswer = new ReconcileMigrateAnswer();
        reconcileMigrateAnswer.setResourceType(ApiCommandResourceType.VirtualMachine);
        reconcileMigrateAnswer.setResourceId(command.getVirtualMachine().getId());

        Long hostId = reconcileCommandVO.getHostId();
        HostVO sourceHost = hostDao.findById(hostId);
        if (sourceHost != null && sourceHost.getStatus() == Status.Up) {
            ReconcileMigrateCommand reconcileMigrateCommand = new ReconcileMigrateCommand(command.getVmName(), true);
            Answer reconcileAnswer = agentManager.easySend(sourceHost.getId(), reconcileMigrateCommand);
            reconcileMigrateAnswer.setSourceHostId(sourceHost.getId());
            if (reconcileAnswer instanceof ReconcileMigrateAnswer) {
                reconcileMigrateAnswer.setStateOnSourceHost(((ReconcileMigrateAnswer) reconcileAnswer).getStateOnSourceHost());
            }
        }

        String destinationIp = command.getDestinationIp();
        HostVO destinationHost = hostDao.findByIp(destinationIp);
        if (destinationHost != null && destinationHost.getStatus() == Status.Up) {
            ReconcileMigrateCommand reconcileMigrateCommand = new ReconcileMigrateCommand(command.getVmName(), false);
            Answer reconcileAnswer = agentManager.easySend(destinationHost.getId(), reconcileMigrateCommand);
            reconcileMigrateAnswer.setDestinationHostId(destinationHost.getId());
            if (reconcileAnswer instanceof ReconcileMigrateAnswer) {
                reconcileMigrateAnswer.setStateOnDestinationHost(((ReconcileMigrateAnswer) reconcileAnswer).getStateOnDestinationHost());
            }
        }

        boolean isReconciled = reconcileMigrateAnswer.getStateOnSourceHost() != null && reconcileMigrateAnswer.getStateOnDestinationHost() != null;
        return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, reconcileMigrateAnswer, isReconciled);
    }

    private ReconcileCommandResult reconcile(ReconcileCommandVO reconcileCommandVO, CopyCommand command) {
        DataTO srcData = command.getSrcTO();
        DataTO destData = command.getDestTO();
        if (srcData == null || destData == null) {
            logger.debug(String.format("Unable to reconcile command %s with srcData %s and destData %s", command, srcData, destData));
            return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, false);
        }
        Long hostId = reconcileCommandVO.getHostId();
        HostVO sourceHost = hostDao.findById(hostId);
        if (sourceHost == null || !Status.Up.equals(sourceHost.getStatus())) {
            return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, false);
        }

        // Send reconcileCommand to the host
        logger.info(String.format("Reconciling command %s via host %s", command, sourceHost.getName()));
        ReconcileCopyCommand reconcileCommand = new ReconcileCopyCommand(srcData, destData, command.getOptions(), command.getOptions2());
        Answer reconcileAnswer = agentManager.easySend(sourceHost.getId(), reconcileCommand);
        if (!(reconcileAnswer instanceof ReconcileAnswer)) {
            return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, true);
        }
        return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, (ReconcileAnswer) reconcileAnswer, true);
    }

    private ReconcileCommandResult reconcile(ReconcileCommandVO reconcileCommandVO, MigrateVolumeCommand command) {
        DataTO srcData = command.getSrcData();
        DataTO destData = command.getDestData();
        if (srcData == null || destData == null) {
            logger.debug(String.format("Unable to reconcile command %s with srcData %s and destData %s", command, srcData, destData));
            return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, false);
        }
        Long hostId = reconcileCommandVO.getHostId();
        HostVO sourceHost = hostDao.findById(hostId);
        if (sourceHost == null || !Status.Up.equals(sourceHost.getStatus())) {
            return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, false);
        }

        // Send reconcileCommand to the host
        logger.info(String.format("Reconciling command %s via host %s", command, sourceHost.getName()));
        ReconcileMigrateVolumeCommand reconcileCommand = new ReconcileMigrateVolumeCommand(srcData, destData);
        Answer reconcileAnswer = agentManager.easySend(sourceHost.getId(), reconcileCommand);
        if (!(reconcileAnswer instanceof ReconcileAnswer)) {
            return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, true);
        }
        return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, (ReconcileAnswer) reconcileAnswer, true);
    }

    @Override
    public void processCommand(Command command) {
        if (command instanceof PingCommand) {
            CommandInfo[] commandInfos = ((PingCommand) command).getCommandInfos();
            for (CommandInfo commandInfo : commandInfos) {
                processCommandInfo(commandInfo);
            }
        }
    }

    private void processCommandInfo(CommandInfo commandInfo) {
        Command parsedCommand = ReconcileCommandUtils.parseCommandInfo(commandInfo);
        Answer parsedAnswer = ReconcileCommandUtils.parseAnswerFromCommandInfo(commandInfo);
        if (parsedCommand != null && parsedCommand.isReconcile()) {
            updateReconcileCommand(commandInfo.getRequestSeq(), parsedCommand, parsedAnswer, null, commandInfo.getState());
        }
    }

    @Override
    public void processAnswers(long requestSeq, Command[] commands, Answer[] answers) {
        if (commands.length != answers.length) {
            logger.error(String.format("Incorrect number of commands (%s) and answers (%s)", commands.length, answers.length));
        }
        for (int i = 0; i < commands.length; i++) {
            Command command = commands[i];
            Answer answer = answers[i];
            if (command.isReconcile() && answer.getResult()) {
                reconcileCommandDao.removeCommand(requestSeq, command.toString(), State.COMPLETED);
            }
        }
    }

    private boolean processReconcileAnswer(long requestSequence, Command cmd, ReconcileAnswer reconcileAnswer) {
        if (cmd instanceof MigrateCommand && reconcileAnswer instanceof ReconcileMigrateAnswer) {
            MigrateCommand command = (MigrateCommand) cmd;
            ReconcileMigrateAnswer answer = (ReconcileMigrateAnswer) reconcileAnswer;
            return processReconcileMigrateAnswer(command, answer);
        } else if (cmd instanceof CopyCommand && reconcileAnswer instanceof ReconcileCopyAnswer) {
            CopyCommand command = (CopyCommand) cmd;
            ReconcileCopyAnswer answer = (ReconcileCopyAnswer) reconcileAnswer;
            return processReconcileCopyAnswer(requestSequence, command, answer);
        } else if (cmd instanceof MigrateVolumeCommand && reconcileAnswer instanceof ReconcileMigrateVolumeAnswer) {
            MigrateVolumeCommand command = (MigrateVolumeCommand) cmd;
            ReconcileMigrateVolumeAnswer answer = (ReconcileMigrateVolumeAnswer) reconcileAnswer;
            return processReconcileMigrateVolumeAnswer(requestSequence, command, answer);
        }
        return true;
    }

    private boolean processReconcileMigrateAnswer(MigrateCommand command, ReconcileMigrateAnswer reconcileAnswer) {
        Long vmId = command.getVirtualMachine().getId();
        VMInstanceVO vm = vmInstanceDao.findById(vmId);
        if (vm == null) {
            logger.debug(String.format("VM (id: %s) has been removed", vmId));
            return true;
        }

        if (!VirtualMachine.State.Migrating.equals(vm.getState())) {
            logger.debug(String.format("VM (id: %s) is not Migrating, the actual state is %s", vmId, vm.getState()));
            return true;
        }

        if (!reconcileAnswer.getSourceHostId().equals(vm.getLastHostId()) || !reconcileAnswer.getDestinationHostId().equals(vm.getHostId())) {
            logger.debug(String.format("VM (id: %s) should have host_id (%s) and last_host_id (%s), but actual host_id is %s) and last_host_id is %s",
                    vmId, reconcileAnswer.getDestinationHostId(), reconcileAnswer.getSourceHostId(), vm.getHostId(), vm.getLastHostId()));
            return true;
        }

        if (reconcileAnswer.getStateOnSourceHost().equals(VirtualMachine.State.Running) && reconcileAnswer.getStateOnDestinationHost().equals(VirtualMachine.State.Stopped)) {
            logger.debug(String.format("VM (id: %s) is Running on source host and Stopped on destination host, mark state as Running on source host", vmId));
            vm.setState(VirtualMachine.State.Running);
            vm.setHostId(reconcileAnswer.getSourceHostId());
            vm.setLastHostId(reconcileAnswer.getDestinationHostId());
            vmInstanceDao.update(vmId, vm);
            return true;
        }

        if (reconcileAnswer.getStateOnSourceHost().equals(VirtualMachine.State.Stopped) && reconcileAnswer.getStateOnDestinationHost().equals(VirtualMachine.State.Running)) {
            logger.debug(String.format("VM (id: %s) is Stopped on source host and Running on destination host, mark state as Running on destination host", vmId));
            vm.setState(VirtualMachine.State.Running);
            vmInstanceDao.update(vmId, vm);
            return true;
        }

        return false;
    }

    private boolean processReconcileCopyAnswer(long requestSequence, CopyCommand command, ReconcileCopyAnswer reconcileAnswer) {
        DataTO srcData = command.getSrcTO();
        DataTO destData = command.getDestTO();
        if (reconcileAnswer.isSkipped()) {
            logger.debug(String.format("The reconcile command for source %s to destination %s is ignored because it is skipped, due to reason: %s", srcData.getId(), destData.getId(), reconcileAnswer.getReason()));
            return true;
        }
        if (!reconcileAnswer.getResult()) {
            logger.debug(String.format("The reconcile command for source %s to destination %s is ignored because the result is false, due to %s", srcData.getId(), destData.getId(), reconcileAnswer.getDetails()));
            return false;
        }
        ReconcileCommandVO reconcileCommandVO = reconcileCommandDao.findCommand(requestSequence, command.toString());
        if (reconcileCommandVO == null) {
            logger.debug(String.format("The reconcile command for source %s to destination %s is not found in database, ignoring", srcData.getId(), destData.getId()));
            return true;
        }
        if (reconcileCommandVO.getAnswerName() == null) {
            logger.debug(String.format("The reconcile command for source %s to destination %s does not have previous answer in database, ignoring this time", srcData.getId(), destData.getId()));
            return false;
        }
        Answer previousAnswer = ReconcileCommandUtils.parseAnswerFromAnswerInfo(reconcileCommandVO.getAnswerName(), reconcileCommandVO.getAnswerInfo());
        if (!(previousAnswer instanceof ReconcileCopyAnswer)) {
            logger.debug(String.format("The reconcile command for source %s to destination %s does not have previous reconcileAnswer in database, ignoring this time", srcData.getId(), destData.getId()));
            return false;
        }
        ReconcileCopyAnswer previousReconcileAnswer = (ReconcileCopyAnswer) previousAnswer;

        VolumeOnStorageTO volumeOnSource = reconcileAnswer.getVolumeOnSource();
        VolumeOnStorageTO volumeOnDestination = reconcileAnswer.getVolumeOnDestination();

        VolumeVO sourceVolume = srcData.getObjectType().equals(DataObjectType.VOLUME) ? volumeDao.findByIdIncludingRemoved(srcData.getId()) : null;
        VolumeVO destVolume = destData.getObjectType().equals(DataObjectType.VOLUME) ? volumeDao.findByIdIncludingRemoved(destData.getId()) : null;
        if (sourceVolume != null && destVolume != null) {
            // TODO
            // copy from primary to primary
            // what's the path of new volume ?
        } else if (sourceVolume == null && destVolume != null) {
            // TODO
            // copy from secondary to primary
        }
        return false;
    }

    private boolean processReconcileMigrateVolumeAnswer(long requestSequence, MigrateVolumeCommand command, ReconcileMigrateVolumeAnswer reconcileAnswer) {
        DataTO srcData = command.getSrcData();
        DataTO destData = command.getDestData();
        if (srcData == null || destData == null) {
            logger.debug(String.format("The source (%s) and destination (%s) of MigrateCommand must be non-empty", srcData, destData));
            return true;
        }
        if (srcData.getId() != destData.getId()) {
            logger.debug(String.format("The source volume (id: %s) and destination volume (id: %s) of MigrateCommand must be same ID", srcData.getId(), destData.getId()));
            return true;
        }
        VolumeVO volumeVO = volumeDao.findByIdIncludingRemoved(srcData.getId());
        if (volumeVO.getRemoved() != null || !VolumeVO.State.Migrating.equals(volumeVO.getState())) {
            logger.debug(String.format("The volume (state: %s) of MigrateCommand must be Migrating", volumeVO.getState()));
            return true;
        }

        if (!(srcData.getDataStore() instanceof PrimaryDataStoreTO) && (destData.getDataStore() instanceof PrimaryDataStoreTO)) {
            logger.debug(String.format("The source (role: %s) and destination (role: %s) of MigrateCommand must be Primary", srcData.getDataStore().getRole(), destData.getDataStore().getRole()));
            return true;
        }

        final PrimaryDataStoreTO srcStore = (PrimaryDataStoreTO) srcData.getDataStore();
        final PrimaryDataStoreTO destStore = (PrimaryDataStoreTO) destData.getDataStore();

        // TODO
        // Case 1: src is null, but dest is not null, update pool_id and path to dest (what state???))
        // Case 2: src is not null, but dest is null, update pool_id and path to source (what state ???)
        // Case 3: src is not null, dest is not null, keep Migrating
        // Others ???

        return false;
    }

}
