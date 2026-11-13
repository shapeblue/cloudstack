//
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
//

package com.cloud.hypervisor.kvm.resource.wrapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.cloudstack.backup.BackupAnswer;
import org.apache.cloudstack.backup.TakeBackupCommand;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.cloudstack.utils.rbd.RbdBackupGuestManager;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.api.Answer;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.hypervisor.kvm.storage.KVMStoragePool;
import com.cloud.hypervisor.kvm.storage.KVMStoragePoolManager;
import com.cloud.storage.Storage;
import com.cloud.utils.Pair;
import com.cloud.utils.script.Script;

@RunWith(MockitoJUnitRunner.class)
public class LibvirtTakeBackupCommandWrapperTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File stateFile;

    private LibvirtTakeBackupCommandWrapper wrapper() throws IOException {
        RbdBackupGuestManager guestManager = new RbdBackupGuestManager(folder.newFolder());
        stateFile = guestManager.getStateFile("test-vm");
        return new LibvirtTakeBackupCommandWrapper() {
            @Override
            protected RbdBackupGuestManager guestManager() {
                return guestManager;
            }
        };
    }

    private LibvirtComputingResource resource() {
        LibvirtComputingResource resource = Mockito.mock(LibvirtComputingResource.class);
        KVMStoragePoolManager poolManager = Mockito.mock(KVMStoragePoolManager.class);
        KVMStoragePool pool = Mockito.mock(KVMStoragePool.class);
        Mockito.lenient().when(pool.getSourceHost()).thenReturn("10.0.0.1");
        Mockito.lenient().when(pool.getSourcePort()).thenReturn(6789);
        Mockito.lenient().when(poolManager.getStoragePool(Mockito.any(), Mockito.anyString())).thenReturn(pool);
        Mockito.lenient().when(resource.getStoragePoolMgr()).thenReturn(poolManager);
        Mockito.lenient().when(resource.getNasBackupPath()).thenReturn("/usr/share/cloudstack-common/scripts/vm/hypervisor/kvm/nasbackup.sh");
        Mockito.lenient().when(resource.getCmdsTimeout()).thenReturn(60);
        return resource;
    }

    private TakeBackupCommand rbdBackupCommand() {
        TakeBackupCommand command = new TakeBackupCommand("test-vm", "test-vm/backup");
        command.setBackupRepoType("nfs");
        command.setBackupRepoAddress("10.0.0.2:/backup");
        command.setMethod("rbd");
        command.setMode("full");
        command.setBitmapNew("1");
        PrimaryDataStoreTO store = Mockito.mock(PrimaryDataStoreTO.class);
        Mockito.lenient().when(store.getPoolType()).thenReturn(Storage.StoragePoolType.RBD);
        Mockito.lenient().when(store.getUuid()).thenReturn("pool-uuid");
        command.setVolumePools(Collections.singletonList(store));
        command.setVolumePaths(Collections.singletonList("rbd-pool/volume"));
        return command;
    }

    private void writeState(String state) throws IOException {
        Files.write(stateFile.toPath(), state.getBytes(StandardCharsets.UTF_8));
    }

    /** Runs a failing RBD backup and returns the virsh calls the wrapper made to release the guest. */
    private List<List<String>> failRbdBackup(LibvirtTakeBackupCommandWrapper wrapper, String domState, Pair<Integer, String> scriptResult) {
        List<List<String>> virshCalls = new ArrayList<>();
        try (MockedStatic<Script> script = Mockito.mockStatic(Script.class)) {
            script.when(() -> Script.getExecutableAbsolutePath(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));
            script.when(() -> Script.executePipedCommands(Mockito.anyList(), Mockito.anyLong())).thenReturn(scriptResult);
            script.when(() -> Script.executeCommand(Mockito.any(String[].class))).thenReturn(domState);
            script.when(() -> Script.executeCommandForExitValue(Mockito.any(String[].class))).thenAnswer(invocation -> {
                Object[] raw = invocation.getArguments();
                List<String> call = new ArrayList<>();
                for (Object part : raw) {
                    if (part instanceof String[]) {
                        call.addAll(java.util.Arrays.asList((String[]) part));
                    } else {
                        call.add(String.valueOf(part));
                    }
                }
                virshCalls.add(call);
                return 0;
            });
            Answer answer = wrapper.execute(rbdBackupCommand(), resource());
            Assert.assertFalse(answer.getResult());
            Assert.assertTrue(answer instanceof BackupAnswer);
        }
        return virshCalls;
    }

    private boolean anyCallContains(List<List<String>> calls, String word) {
        return calls.stream().anyMatch(call -> call.contains(word));
    }

    @Test
    public void aTimedOutRbdBackupResumesTheGuestItPaused() throws IOException {
        LibvirtTakeBackupCommandWrapper wrapper = wrapper();
        writeState("suspended\n");

        List<List<String>> calls = failRbdBackup(wrapper, "paused\n", new Pair<>(-1, Script.ERR_TIMEOUT));

        Assert.assertTrue("the guest is resumed: " + calls, anyCallContains(calls, "resume"));
        Assert.assertFalse("the guest was not frozen: " + calls, anyCallContains(calls, "qemu-agent-command"));
        Assert.assertFalse("the state file is removed once the guest is released", stateFile.exists());
    }

    @Test
    public void aTimedOutRbdBackupThawsTheGuestItFroze() throws IOException {
        LibvirtTakeBackupCommandWrapper wrapper = wrapper();
        writeState("frozen\n");

        List<List<String>> calls = failRbdBackup(wrapper, "running\n", new Pair<>(-1, Script.ERR_TIMEOUT));

        Assert.assertTrue("the guest is thawed: " + calls, anyCallContains(calls, "qemu-agent-command"));
        Assert.assertFalse("a running guest is not resumed: " + calls, anyCallContains(calls, "resume"));
        Assert.assertFalse(stateFile.exists());
    }

    @Test
    public void aGuestPausedByAnOperatorIsNotResumedWhenTheBackupHeldNoGuest() throws IOException {
        LibvirtTakeBackupCommandWrapper wrapper = wrapper();
        // No state file: the script never paused or froze the guest, so the guest is none of its business.

        List<List<String>> calls = failRbdBackup(wrapper, "paused\n", new Pair<>(-1, Script.ERR_TIMEOUT));

        Assert.assertTrue("nothing is done to the guest: " + calls, calls.isEmpty());
    }

    @Test
    public void aGuestThatIsNoLongerPausedIsNotResumed() throws IOException {
        LibvirtTakeBackupCommandWrapper wrapper = wrapper();
        // The script paused it, resumed it, and was killed afterwards: the state file is stale.
        writeState("suspended\n");

        List<List<String>> calls = failRbdBackup(wrapper, "running\n", new Pair<>(-1, Script.ERR_TIMEOUT));

        Assert.assertFalse("a running guest is not resumed: " + calls, anyCallContains(calls, "resume"));
        Assert.assertFalse(stateFile.exists());
    }

    @Test
    public void aBackupRefusedBecauseAnotherOneIsRunningLeavesTheGuestToThatBackup() throws IOException {
        LibvirtTakeBackupCommandWrapper wrapper = wrapper();
        // The state file belongs to the backup which holds the lock, not to the one which was refused.
        writeState("suspended\nfrozen\n");

        List<List<String>> calls = failRbdBackup(wrapper, "paused\n", new Pair<>(21, "Another backup of vm test-vm is already running on this host"));

        Assert.assertTrue("the guest is not touched: " + calls, calls.isEmpty());
        Assert.assertTrue("the state file of the running backup is kept", stateFile.exists());
    }
}
