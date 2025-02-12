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
import com.cloud.agent.api.PingAnswer;
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
import com.cloud.storage.Volume;
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
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPoint;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPointSelector;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.storage.command.CommandResult;
import org.apache.cloudstack.storage.command.CopyCommand;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.cloudstack.storage.volume.VolumeOnStorageTO;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.commons.collections.MapUtils;
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
    @Inject
    EndPointSelector endPointSelector;
    @Inject
    DataStoreManager dataStoreManager;

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
            reconcileCommandDao.updateCommandsToInterruptedByManagementServerId(ManagementServerId);
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
    public boolean updateReconcileCommand(long requestSeq, Command command, Answer answer, State newStateByManagement, State newStateByAgent) {
        String commandKey = getCommandKey(requestSeq, command);
        logger.debug(String.format("Updating reconcile command %s with answer %s and new states %s-%s", commandKey, answer, newStateByManagement, newStateByAgent));
        ReconcileCommandVO reconcileCommandVO = reconcileCommandDao.findCommand(requestSeq, command.toString());
        if (reconcileCommandVO == null) {
            return false;
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
        return true;
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

    private EndPoint getEndpoint(DataTO srcData, DataTO destData) {
        EndPoint endPoint = null;
        if (srcData.getDataStore() instanceof PrimaryDataStoreTO) {
            PrimaryDataStoreTO srcDataStore = (PrimaryDataStoreTO) srcData.getDataStore();
            DataStore store = dataStoreManager.getPrimaryDataStore(srcDataStore.getId());
            endPoint = endPointSelector.select(store);
        } else if (destData.getDataStore() instanceof PrimaryDataStoreTO) {
            PrimaryDataStoreTO destDataStore = (PrimaryDataStoreTO) destData.getDataStore();
            DataStore store = dataStoreManager.getPrimaryDataStore(destDataStore.getId());
            endPoint = endPointSelector.select(store);
        }
        return endPoint;
    }

    private ReconcileCommandResult reconcile(ReconcileCommandVO reconcileCommandVO, CopyCommand command) {
        DataTO srcData = command.getSrcTO();
        DataTO destData = command.getDestTO();
        if (srcData == null || destData == null) {
            logger.debug(String.format("Unable to reconcile command %s with srcData %s and destData %s", command, srcData, destData));
            return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, false);
        }
        Long hostId = reconcileCommandVO.getHostId();
        HostVO host = hostDao.findById(hostId);
        if (host == null || !Status.Up.equals(host.getStatus())) {
            EndPoint endPoint = getEndpoint(srcData, destData);
            if (endPoint == null) {
                return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, false);
            }
            host = hostDao.findById(endPoint.getId());
        }

        // Send reconcileCommand to the host
        logger.info(String.format("Reconciling command %s via host %s", command, host.getName()));
        ReconcileCopyCommand reconcileCommand = new ReconcileCopyCommand(srcData, destData, command.getOptions(), command.getOptions2());
        Answer reconcileAnswer = agentManager.easySend(host.getId(), reconcileCommand);
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
        HostVO host = hostDao.findById(hostId);
        if (host == null || !Status.Up.equals(host.getStatus())) {
            EndPoint endPoint = getEndpoint(srcData, destData);
            if (endPoint == null) {
                return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, false);
            }
            host = hostDao.findById(endPoint.getId());
        }

        // Send reconcileCommand to the host
        logger.info(String.format("Reconciling command %s via host %s", command, host.getName()));
        ReconcileMigrateVolumeCommand reconcileCommand = new ReconcileMigrateVolumeCommand(srcData, destData);
        Answer reconcileAnswer = agentManager.easySend(host.getId(), reconcileCommand);
        if (!(reconcileAnswer instanceof ReconcileAnswer)) {
            return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, null, true);
        }
        return new ReconcileCommandResult(reconcileCommandVO.getRequestSequence(), command, (ReconcileAnswer) reconcileAnswer, true);
    }

    @Override
    public void processCommand(Command pingCommand, Answer pingAnswer) {
        if (pingCommand instanceof PingCommand && pingAnswer instanceof PingAnswer) {
            CommandInfo[] commandInfos = ((PingCommand) pingCommand).getCommandInfos();
            for (CommandInfo commandInfo : commandInfos) {
                processCommandInfo(commandInfo, (PingAnswer) pingAnswer);
            }
        }
    }

    private void processCommandInfo(CommandInfo commandInfo, PingAnswer pingAnswer) {
        Command parsedCommand = ReconcileCommandUtils.parseCommandInfo(commandInfo);
        Answer parsedAnswer = ReconcileCommandUtils.parseAnswerFromCommandInfo(commandInfo);
        if (parsedCommand != null && parsedCommand.isReconcile()) {
            if (updateReconcileCommand(commandInfo.getRequestSeq(), parsedCommand, parsedAnswer, null, commandInfo.getState())) {
                pingAnswer.addReconcileCommand(getCommandKey(commandInfo.getRequestSeq(), parsedCommand));
            }
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

    @Override
    public void updateReconcileCommandToInterruptedByManagementServerId(long managementServerId) {
        logger.debug("Updating reconcile command to interrupted by management server id " + managementServerId);
        reconcileCommandDao.updateCommandsToInterruptedByManagementServerId(managementServerId);
    }

    @Override
    public void updateReconcileCommandToInterruptedByHostId(long hostId) {
        logger.debug("Updating reconcile command to interrupted by host id " + hostId);
        reconcileCommandDao.updateCommandsToInterruptedByHostId(hostId);
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

            Map<String, MigrateCommand.MigrateDiskInfo> migrateDiskInfoMap = command.getMigrateStorage();
            if (MapUtils.isNotEmpty(migrateDiskInfoMap)) {
                for (Map.Entry<String, MigrateCommand.MigrateDiskInfo> entry : migrateDiskInfoMap.entrySet()) {
                    VolumeVO volumeVO = volumeDao.findOneByPathAndState(entry.getKey(), Volume.State.Migrating);
                    if (volumeVO != null) {
                        logger.debug(String.format("Adding volume %s to vm %s as part of reconciliation of command %s and answer %s", volumeVO.getId(), vmId, command, reconcileAnswer));
                        volumeVO.setState(Volume.State.Ready);
                        volumeVO.setInstanceId(vmId);
                        volumeDao.update(volumeVO.getId(), volumeVO);
                    }
                }
                List<VolumeVO> migratingVolumes = volumeDao.listByInstanceIdAndState(vmId, Volume.State.Migrating);
                for (VolumeVO volumeVO : migratingVolumes) {
                    logger.debug(String.format("Removing volume %s from vm %s as part of reconciliation of command %s and answer %s", volumeVO.getId(), vmId, command, reconcileAnswer));
                    volumeVO.setState(Volume.State.Destroy);
                    volumeVO.setVolumeType(Volume.Type.DATADISK);
                    volumeVO.setInstanceId(null);
                    volumeDao.update(volumeVO.getId(), volumeVO);
                }
            }
            return true;
        }

        if (reconcileAnswer.getStateOnSourceHost().equals(VirtualMachine.State.Stopped) && reconcileAnswer.getStateOnDestinationHost().equals(VirtualMachine.State.Running)) {
            logger.debug(String.format("VM (id: %s) is Stopped on source host and Running on destination host, mark state as Running on destination host", vmId));
            vm.setState(VirtualMachine.State.Running);
            vmInstanceDao.update(vmId, vm);

            Map<String, MigrateCommand.MigrateDiskInfo> migrateDiskInfoMap = command.getMigrateStorage();
            if (MapUtils.isNotEmpty(migrateDiskInfoMap)) {
                for (Map.Entry<String, MigrateCommand.MigrateDiskInfo> entry : migrateDiskInfoMap.entrySet()) {
                    VolumeVO volumeVO = volumeDao.findOneByPathAndState(entry.getKey(), Volume.State.Migrating);
                    if (volumeVO != null) {
                        logger.debug(String.format("Removing volume %s from vm %s as part of reconciliation of command %s and answer %s", volumeVO.getId(), vmId, command, reconcileAnswer));
                        volumeVO.setState(Volume.State.Destroy);
                        volumeVO.setVolumeType(Volume.Type.DATADISK);
                        volumeVO.setInstanceId(null);
                        volumeDao.update(volumeVO.getId(), volumeVO);
                    }
                }
                List<VolumeVO> migratingVolumes = volumeDao.listByInstanceIdAndState(vmId, Volume.State.Migrating);
                for (VolumeVO volumeVO : migratingVolumes) {
                    logger.debug(String.format("Marking volume %s of vm %s to Ready as part of reconciliation of command %s and answer %s", volumeVO.getId(), vmId, command, reconcileAnswer));
                    volumeVO.setState(Volume.State.Ready);
                    volumeDao.update(volumeVO.getId(), volumeVO);
                }
            }
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
        VolumeOnStorageTO previousVolumeOnDestination = previousReconcileAnswer.getVolumeOnDestination();

        VolumeOnStorageTO volumeOnSource = reconcileAnswer.getVolumeOnSource();
        VolumeOnStorageTO volumeOnDestination = reconcileAnswer.getVolumeOnDestination();

        VolumeVO sourceVolume = srcData.getObjectType().equals(DataObjectType.VOLUME) ? volumeDao.findByIdIncludingRemoved(srcData.getId()) : null;
        VolumeVO destVolume = destData.getObjectType().equals(DataObjectType.VOLUME) ? volumeDao.findByIdIncludingRemoved(destData.getId()) : null;
        if (sourceVolume != null && destVolume != null) {
            // copy from primary to primary
            boolean isDestinationVolumeChanged = (volumeOnDestination != null && previousVolumeOnDestination != null && volumeOnDestination.getSize() != previousVolumeOnDestination.getSize());
            if (!isDestinationVolumeChanged) {
                if (sourceVolume.getRemoved() == null && sourceVolume.getState().equals(Volume.State.Migrating) && volumeOnSource != null) {
                    sourceVolume.setState(Volume.State.Ready);
                    volumeDao.update(sourceVolume.getId(), sourceVolume);
                }
                if (destVolume.getRemoved() != null && destVolume.getState().equals(Volume.State.Migrating) && volumeOnDestination != null && sourceVolume.getRemoved() == null) {
                    destVolume.setState(Volume.State.Destroy);
                    volumeDao.update(destVolume.getId(), destVolume);
                }
            } else if (destVolume.getRemoved() != null && destVolume.getState().equals(Volume.State.Migrating) && volumeOnDestination != null) {
                destVolume.setPath(volumeOnDestination.getPath());
                volumeDao.update(destVolume.getId(), destVolume);
            }
        } else if (sourceVolume == null && destVolume != null) {
            // copy from secondary to primary
            boolean isDestinationVolumeChanged = (volumeOnDestination != null && previousVolumeOnDestination != null && volumeOnDestination.getSize() != previousVolumeOnDestination.getSize());
            if (!isDestinationVolumeChanged) {
                if (destVolume.getRemoved() != null && destVolume.getState().equals(Volume.State.Migrating) && volumeOnDestination != null) {
                    destVolume.setState(Volume.State.Destroy);
                    volumeDao.update(destVolume.getId(), destVolume);
                }
            } else if (destVolume.getRemoved() != null && destVolume.getState().equals(Volume.State.Migrating) && volumeOnDestination != null) {
                destVolume.setPath(volumeOnDestination.getPath());
                volumeDao.update(destVolume.getId(), destVolume);
            }
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
        if (volumeVO.getRemoved() != null || !Volume.State.Migrating.equals(volumeVO.getState())) {
            logger.debug(String.format("The volume (state: %s) of MigrateCommand must be Migrating", volumeVO.getState()));
            return true;
        }

        if (!(srcData.getDataStore() instanceof PrimaryDataStoreTO) && (destData.getDataStore() instanceof PrimaryDataStoreTO)) {
            logger.debug(String.format("The source (role: %s) and destination (role: %s) of MigrateCommand must be Primary", srcData.getDataStore().getRole(), destData.getDataStore().getRole()));
            return true;
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
        if (!(previousAnswer instanceof ReconcileMigrateVolumeAnswer)) {
            logger.debug(String.format("The reconcile command for source %s to destination %s does not have previous reconcileAnswer in database, ignoring this time", srcData.getId(), destData.getId()));
            return false;
        }

        ReconcileMigrateVolumeAnswer previousReconcileAnswer = (ReconcileMigrateVolumeAnswer) previousAnswer;
        VolumeOnStorageTO previousVolumeOnDestination = previousReconcileAnswer.getVolumeOnDestination();

        VolumeOnStorageTO volumeOnSource = reconcileAnswer.getVolumeOnSource();
        VolumeOnStorageTO volumeOnDestination = reconcileAnswer.getVolumeOnDestination();

        VolumeVO sourceVolume = srcData.getObjectType().equals(DataObjectType.VOLUME) ? volumeDao.findByIdIncludingRemoved(srcData.getId()) : null;
        if (sourceVolume == null || sourceVolume.getRemoved() != null) {
            logger.debug(String.format("Volume (id: %s) has been removed in CloudStack", srcData.getId()));
            return true;
        }

        if (!sourceVolume.getState().equals(Volume.State.Migrating)) {
            logger.debug(String.format("Volume (id: %s) is not in Migrating state (state: %s)", srcData.getId(), sourceVolume.getState()));
            return true;
        }

        final PrimaryDataStoreTO srcStore = (PrimaryDataStoreTO) srcData.getDataStore();
        final PrimaryDataStoreTO destStore = (PrimaryDataStoreTO) destData.getDataStore();

        if (volumeOnSource != null) {
            boolean isDestinationVolumeChanged = (volumeOnDestination != null && previousVolumeOnDestination != null && volumeOnDestination.getSize() != previousVolumeOnDestination.getSize());
            if (!isDestinationVolumeChanged) {
                logger.debug(String.format("Volume (id :%s) on destination (id: %s) is not updated", srcData.getId(), destStore.getId()));
                sourceVolume.setState(Volume.State.Ready);
                sourceVolume.setPoolId(sourceVolume.getLastPoolId());   // restore pool_id, path is the same
                volumeDao.update(sourceVolume.getId(), sourceVolume);
                return true;
            } else {
                logger.debug(String.format("Volume (id :%s) on destination (id: %s) is still being updated, skipping", srcData.getId(), destStore.getId()));
                return false;
            }
        } else if (volumeOnDestination != null) {
            logger.debug(String.format("Volume (id: %s) does not exist on source (id: %s) but exist on destination (id: %s), updating state to Ready on destination", srcData.getId(), srcStore.getId(), destStore.getId()));
            sourceVolume.setState(Volume.State.Ready);
            volumeDao.update(sourceVolume.getId(), sourceVolume);
            return true;
        }

        return false;
    }

}
