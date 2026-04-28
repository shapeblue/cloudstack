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
package org.apache.cloudstack.service;

import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opendaylight.aaa.cert.api.ICertificateManager;
import org.opendaylight.ovsdb.lib.OvsdbClient;
import org.opendaylight.ovsdb.lib.impl.NettyBootstrapFactoryImpl;
import org.opendaylight.ovsdb.lib.impl.OvsdbConnectionService;
import org.opendaylight.ovsdb.lib.notation.Mutator;
import org.opendaylight.ovsdb.lib.notation.Row;
import org.opendaylight.ovsdb.lib.notation.UUID;
import org.opendaylight.ovsdb.lib.operations.DefaultOperations;
import org.opendaylight.ovsdb.lib.operations.Insert;
import org.opendaylight.ovsdb.lib.operations.Operation;
import org.opendaylight.ovsdb.lib.operations.OperationResult;
import org.opendaylight.ovsdb.lib.operations.Operations;
import org.opendaylight.ovsdb.lib.schema.ColumnSchema;
import org.opendaylight.ovsdb.lib.schema.DatabaseSchema;
import org.opendaylight.ovsdb.lib.schema.GenericTableSchema;

import javax.annotation.PreDestroy;
import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OvnNbClient {
    protected static final Logger logger = LogManager.getLogger(OvnNbClient.class);
    private static final String NORTHBOUND_DB = "OVN_Northbound";
    private static final String LOGICAL_SWITCH_TABLE = "Logical_Switch";
    private static final String LOGICAL_SWITCH_PORT_TABLE = "Logical_Switch_Port";
    private static final String DHCP_OPTIONS_TABLE = "DHCP_Options";
    private static final long DEFAULT_TIMEOUT_MS = 5_000L;
    private static final Pattern CONN_PATTERN = Pattern.compile("^(tcp|ssl):([^:]+):([0-9]+)$");
    private static final ICertificateManager NOOP_CERT_MANAGER = new NoopCertificateManager();
    private static final Operations OVSDB_OPS = new DefaultOperations();

    private final long timeoutMs;
    private NettyBootstrapFactoryImpl bootstrapFactory;
    private OvsdbConnectionService tcpConnectionService;

    public OvnNbClient() {
        this(DEFAULT_TIMEOUT_MS);
    }

    public OvnNbClient(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isValidConnectionString(String connection) {
        if (StringUtils.isBlank(connection)) {
            return false;
        }
        return CONN_PATTERN.matcher(connection).matches() || connection.startsWith("unix:/");
    }

    /**
     * Opens a transient connection to NB, runs an echo, lists the databases, and disconnects.
     * Throws on failure - caller treats success as proof that the NB endpoint is reachable
     * and the supplied credentials/certificates are valid.
     */
    public void verifyConnection(String nbConnection, String caCertPath, String clientCertPath, String clientPrivateKeyPath) {
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            client.echo().get(timeoutMs, TimeUnit.MILLISECONDS);
            List<String> dbs = client.getDatabases().get(timeoutMs, TimeUnit.MILLISECONDS);
            if (dbs == null || !dbs.contains(NORTHBOUND_DB)) {
                throw new CloudRuntimeException(String.format("OVN endpoint %s did not advertise %s; got %s",
                        nbConnection, NORTHBOUND_DB, dbs));
            }
            logger.debug("OVN NB at {} reachable, databases={}", nbConnection, dbs);
            return null;
        });
    }

    /**
     * Creates a Logical_Switch with the given name and external_ids in the OVN_Northbound database
     * exposed at {@code nbConnection}. Idempotent: if a switch with the same name already exists,
     * the call succeeds without modifying it. Uses the native OVSDB JSON-RPC protocol.
     */
    public void createLogicalSwitch(String nbConnection, String caCertPath, String clientCertPath,
                                    String clientPrivateKeyPath, String logicalSwitchName, Map<String, String> externalIds) {
        if (StringUtils.isBlank(logicalSwitchName)) {
            throw new CloudRuntimeException("Logical switch name is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema ls = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> nameCol = ls.column("name", String.class);

            if (logicalSwitchExists(client, schema, ls, nameCol, logicalSwitchName)) {
                logger.debug("Logical_Switch [{}] already exists on {} - skipping create", logicalSwitchName, nbConnection);
                return null;
            }

            Insert<GenericTableSchema> insert = OVSDB_OPS.insert(ls)
                    .value(nameCol, logicalSwitchName);
            if (externalIds != null && !externalIds.isEmpty()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                ColumnSchema<GenericTableSchema, Map> extIdsCol = ls.column("external_ids", Map.class);
                insert = insert.value(extIdsCol, new HashMap<>(externalIds));
            }
            List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(insert))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("create Logical_Switch %s", logicalSwitchName));
            logger.info("Created OVN Logical_Switch [{}] at {}", logicalSwitchName, nbConnection);
            return null;
        });
    }

    /**
     * Removes a Logical_Switch by name. Idempotent: missing switch is treated as a successful no-op.
     */
    public void deleteLogicalSwitch(String nbConnection, String caCertPath, String clientCertPath,
                                    String clientPrivateKeyPath, String logicalSwitchName) {
        if (StringUtils.isBlank(logicalSwitchName)) {
            throw new CloudRuntimeException("Logical switch name is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema ls = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> nameCol = ls.column("name", String.class);

            if (!logicalSwitchExists(client, schema, ls, nameCol, logicalSwitchName)) {
                logger.debug("Logical_Switch [{}] not present on {} - nothing to delete", logicalSwitchName, nbConnection);
                return null;
            }

            Operation<GenericTableSchema> delete = OVSDB_OPS.delete(ls)
                    .where(nameCol.opEqual(logicalSwitchName)).build();
            List<OperationResult> results = client.transact(schema, Collections.singletonList(delete))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("delete Logical_Switch %s", logicalSwitchName));
            logger.info("Deleted OVN Logical_Switch [{}] at {}", logicalSwitchName, nbConnection);
            return null;
        });
    }

    /**
     * Creates a Logical_Switch_Port on the named Logical_Switch and binds it.
     * The LSP {@code addresses} and {@code port_security} columns are seeded with the supplied
     * MAC and (optional) IPv4. Idempotent: if an LSP with the same name already exists in the NB
     * database, the call succeeds without modifying the row. The {@code iface-id} that ovn-controller
     * looks for on the local OVS port should match {@code lspName}.
     */
    public void createLogicalSwitchPort(String nbConnection, String caCertPath, String clientCertPath,
                                        String clientPrivateKeyPath,
                                        String logicalSwitchName, String lspName,
                                        String mac, String ipv4, Map<String, String> externalIds) {
        if (StringUtils.isBlank(logicalSwitchName) || StringUtils.isBlank(lspName)) {
            throw new CloudRuntimeException("Logical switch / port name is blank");
        }
        if (StringUtils.isBlank(mac)) {
            throw new CloudRuntimeException("MAC is required for Logical_Switch_Port " + lspName);
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lsTable = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            GenericTableSchema lspTable = schema.table(LOGICAL_SWITCH_PORT_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lsNameCol = lsTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> lspNameCol = lspTable.column("name", String.class);

            if (logicalSwitchPortExists(client, schema, lspTable, lspNameCol, lspName)) {
                logger.debug("Logical_Switch_Port [{}] already exists on {} - skipping create", lspName, nbConnection);
                return null;
            }

            String addressEntry = StringUtils.isNotBlank(ipv4) ? mac + " " + ipv4 : mac;
            ColumnSchema<GenericTableSchema, Set<String>> addressesCol = lspTable.multiValuedColumn("addresses", String.class);
            ColumnSchema<GenericTableSchema, Set<String>> portSecCol = lspTable.multiValuedColumn("port_security", String.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> portsCol = lsTable.multiValuedColumn("ports", UUID.class);

            String namedUuid = "newlsp";
            Insert<GenericTableSchema> insertLsp = OVSDB_OPS.insert(lspTable)
                    .withId(namedUuid)
                    .value(lspNameCol, lspName)
                    .value(addressesCol, Collections.singleton(addressEntry))
                    .value(portSecCol, Collections.singleton(addressEntry));
            if (externalIds != null && !externalIds.isEmpty()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                ColumnSchema<GenericTableSchema, Map> extIdsCol = lspTable.column("external_ids", Map.class);
                insertLsp = insertLsp.value(extIdsCol, new HashMap<>(externalIds));
            }

            UUID lspRef = new UUID(namedUuid);
            Operation<GenericTableSchema> mutateLs = OVSDB_OPS.mutate(lsTable)
                    .addMutation(portsCol, Mutator.INSERT, Collections.singleton(lspRef))
                    .where(lsNameCol.opEqual(logicalSwitchName)).build();

            List<OperationResult> results = client.transact(schema,
                            Arrays.<Operation>asList(insertLsp, mutateLs))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("create Logical_Switch_Port %s on %s", lspName, logicalSwitchName));
            logger.info("Created OVN Logical_Switch_Port [{}] on Logical_Switch [{}] at {}",
                    lspName, logicalSwitchName, nbConnection);
            return null;
        });
    }

    /**
     * Removes a Logical_Switch_Port by name and detaches it from its parent Logical_Switch.
     * Idempotent: missing LSP is treated as a successful no-op.
     */
    public void deleteLogicalSwitchPort(String nbConnection, String caCertPath, String clientCertPath,
                                        String clientPrivateKeyPath,
                                        String logicalSwitchName, String lspName) {
        if (StringUtils.isBlank(lspName)) {
            throw new CloudRuntimeException("Logical_Switch_Port name is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lsTable = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            GenericTableSchema lspTable = schema.table(LOGICAL_SWITCH_PORT_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lsNameCol = lsTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> lspNameCol = lspTable.column("name", String.class);

            UUID lspUuid = findLspUuid(client, schema, lspTable, lspNameCol, lspName);
            if (lspUuid == null) {
                logger.debug("Logical_Switch_Port [{}] not present on {} - nothing to delete", lspName, nbConnection);
                return null;
            }

            ColumnSchema<GenericTableSchema, Set<UUID>> portsCol = lsTable.multiValuedColumn("ports", UUID.class);

            List<Operation> ops = new ArrayList<>();
            if (StringUtils.isNotBlank(logicalSwitchName)) {
                ops.add(OVSDB_OPS.mutate(lsTable)
                        .addMutation(portsCol, Mutator.DELETE, Collections.singleton(lspUuid))
                        .where(lsNameCol.opEqual(logicalSwitchName)).build());
            }
            ops.add(OVSDB_OPS.delete(lspTable)
                    .where(lspNameCol.opEqual(lspName)).build());

            List<OperationResult> results = client.transact(schema, ops)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("delete Logical_Switch_Port %s", lspName));
            logger.info("Deleted OVN Logical_Switch_Port [{}] at {}", lspName, nbConnection);
            return null;
        });
    }

    private boolean logicalSwitchPortExists(OvsdbClient client, DatabaseSchema schema,
                                            GenericTableSchema lspTable,
                                            ColumnSchema<GenericTableSchema, String> nameCol,
                                            String name) throws Exception {
        Operation<GenericTableSchema> select = OVSDB_OPS.select(lspTable)
                .column(nameCol)
                .where(nameCol.opEqual(name)).build();
        List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(select))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        if (results == null || results.isEmpty()) {
            return false;
        }
        OperationResult r = results.get(0);
        if (r.getError() != null) {
            throw new CloudRuntimeException("OVSDB select failed for Logical_Switch_Port " + name + ": " + r.getError());
        }
        List<Row<GenericTableSchema>> rows = r.getRows();
        return rows != null && !rows.isEmpty();
    }

    private UUID findLspUuid(OvsdbClient client, DatabaseSchema schema,
                             GenericTableSchema lspTable,
                             ColumnSchema<GenericTableSchema, String> nameCol,
                             String name) throws Exception {
        ColumnSchema<GenericTableSchema, UUID> uuidCol = lspTable.column("_uuid", UUID.class);
        Operation<GenericTableSchema> select = OVSDB_OPS.select(lspTable)
                .column(uuidCol)
                .where(nameCol.opEqual(name)).build();
        List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(select))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        if (results == null || results.isEmpty()) {
            return null;
        }
        OperationResult r = results.get(0);
        if (r.getError() != null) {
            throw new CloudRuntimeException("OVSDB select failed for LSP _uuid lookup " + name + ": " + r.getError());
        }
        List<Row<GenericTableSchema>> rows = r.getRows();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.get(0).getColumn(uuidCol).getData();
    }

    /**
     * Creates (or returns the UUID of an existing) DHCP_Options row identified by
     * {@code external_ids:cloudstack_network_id}. Idempotent: if a row already matches the
     * external_ids tag for this network, no new row is created and the existing UUID is returned.
     */
    public String createDhcpOptions(String nbConnection, String caCertPath, String clientCertPath,
                                    String clientPrivateKeyPath,
                                    String cidr, Map<String, String> options, Map<String, String> externalIds) {
        if (StringUtils.isBlank(cidr)) {
            throw new CloudRuntimeException("DHCP_Options cidr is blank");
        }
        if (externalIds == null || !externalIds.containsKey("cloudstack_network_id")) {
            throw new CloudRuntimeException("DHCP_Options external_ids must include cloudstack_network_id");
        }
        final String networkId = externalIds.get("cloudstack_network_id");
        return runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema dhcpTable = schema.table(DHCP_OPTIONS_TABLE, GenericTableSchema.class);

            UUID existing = findDhcpOptionsByNetworkId(client, schema, dhcpTable, networkId);
            if (existing != null) {
                logger.debug("DHCP_Options for network [{}] already exists ({}) on {} - skipping create",
                        networkId, existing, nbConnection);
                return existing.toString();
            }

            ColumnSchema<GenericTableSchema, String> cidrCol = dhcpTable.column("cidr", String.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> optionsCol = dhcpTable.column("options", Map.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> extIdsCol = dhcpTable.column("external_ids", Map.class);

            String namedUuid = "newdhcp";
            Insert<GenericTableSchema> insert = OVSDB_OPS.insert(dhcpTable)
                    .withId(namedUuid)
                    .value(cidrCol, cidr)
                    .value(extIdsCol, new HashMap<>(externalIds));
            if (options != null && !options.isEmpty()) {
                insert = insert.value(optionsCol, new HashMap<>(options));
            }
            List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(insert))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("create DHCP_Options for network %s", networkId));
            UUID created = results.get(0).getUuid();
            logger.info("Created OVN DHCP_Options [{}] for network [{}] cidr=[{}] at {}",
                    created, networkId, cidr, nbConnection);
            return created != null ? created.toString() : null;
        });
    }

    /**
     * Removes the DHCP_Options row tagged with {@code external_ids:cloudstack_network_id=networkId}.
     * Idempotent: missing row is a no-op.
     */
    public void deleteDhcpOptions(String nbConnection, String caCertPath, String clientCertPath,
                                  String clientPrivateKeyPath, String networkId) {
        if (StringUtils.isBlank(networkId)) {
            throw new CloudRuntimeException("Network id is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema dhcpTable = schema.table(DHCP_OPTIONS_TABLE, GenericTableSchema.class);
            UUID existing = findDhcpOptionsByNetworkId(client, schema, dhcpTable, networkId);
            if (existing == null) {
                logger.debug("DHCP_Options for network [{}] not present on {} - nothing to delete",
                        networkId, nbConnection);
                return null;
            }
            ColumnSchema<GenericTableSchema, UUID> uuidCol = dhcpTable.column("_uuid", UUID.class);
            Operation<GenericTableSchema> delete = OVSDB_OPS.delete(dhcpTable)
                    .where(uuidCol.opEqual(existing)).build();
            List<OperationResult> results = client.transact(schema, Collections.singletonList(delete))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("delete DHCP_Options for network %s", networkId));
            logger.info("Deleted OVN DHCP_Options [{}] for network [{}] at {}", existing, networkId, nbConnection);
            return null;
        });
    }

    /**
     * Sets the {@code dhcpv4_options} reference of a Logical_Switch_Port to the given DHCP_Options UUID,
     * causing ovn-controller to answer DHCPv4 requests on that port from the DHCP_Options row.
     */
    public void setLspDhcpv4Options(String nbConnection, String caCertPath, String clientCertPath,
                                    String clientPrivateKeyPath, String lspName, String dhcpOptionsUuid) {
        if (StringUtils.isBlank(lspName) || StringUtils.isBlank(dhcpOptionsUuid)) {
            throw new CloudRuntimeException("LSP name or DHCP_Options uuid is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lspTable = schema.table(LOGICAL_SWITCH_PORT_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lspNameCol = lspTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> dhcpCol = lspTable.multiValuedColumn("dhcpv4_options", UUID.class);

            UUID dhcpRef = new UUID(dhcpOptionsUuid);
            Operation<GenericTableSchema> update = OVSDB_OPS.update(lspTable)
                    .set(dhcpCol, Collections.singleton(dhcpRef))
                    .where(lspNameCol.opEqual(lspName)).build();
            List<OperationResult> results = client.transact(schema, Collections.singletonList(update))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("set dhcpv4_options=%s on LSP %s", dhcpOptionsUuid, lspName));
            logger.debug("Set dhcpv4_options=[{}] on Logical_Switch_Port [{}] at {}", dhcpOptionsUuid, lspName, nbConnection);
            return null;
        });
    }

    private UUID findDhcpOptionsByNetworkId(OvsdbClient client, DatabaseSchema schema,
                                            GenericTableSchema dhcpTable, String networkId) throws Exception {
        ColumnSchema<GenericTableSchema, UUID> uuidCol = dhcpTable.column("_uuid", UUID.class);
        // Native OVSDB conditions on map values are awkward via the ODL operations API, so we
        // pull every DHCP_Options row's external_ids and filter client-side. The DHCP_Options
        // table is small enough that this is acceptable.
        @SuppressWarnings({"rawtypes", "unchecked"})
        ColumnSchema<GenericTableSchema, Map> extIdsCol = dhcpTable.column("external_ids", Map.class);
        Operation<GenericTableSchema> selectAll = OVSDB_OPS.select(dhcpTable).column(uuidCol).column(extIdsCol);
        List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(selectAll))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        if (results == null || results.isEmpty()) return null;
        OperationResult r = results.get(0);
        if (r.getError() != null) {
            throw new CloudRuntimeException("OVSDB select on DHCP_Options failed: " + r.getError());
        }
        if (r.getRows() == null) return null;
        for (Row<GenericTableSchema> row : r.getRows()) {
            @SuppressWarnings("unchecked")
            Map<String, String> ext = (Map<String, String>) row.getColumn(extIdsCol).getData();
            if (ext != null && networkId.equals(ext.get("cloudstack_network_id"))) {
                return row.getColumn(uuidCol).getData();
            }
        }
        return null;
    }

    private boolean logicalSwitchExists(OvsdbClient client, DatabaseSchema schema,
                                        GenericTableSchema ls, ColumnSchema<GenericTableSchema, String> nameCol,
                                        String name) throws Exception {
        Operation<GenericTableSchema> select = OVSDB_OPS.select(ls)
                .column(nameCol)
                .where(nameCol.opEqual(name)).build();
        List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(select))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        if (results == null || results.isEmpty()) {
            return false;
        }
        OperationResult r = results.get(0);
        if (r.getError() != null) {
            throw new CloudRuntimeException("OVSDB select failed for Logical_Switch " + name + ": " + r.getError());
        }
        List<Row<GenericTableSchema>> rows = r.getRows();
        return rows != null && !rows.isEmpty();
    }

    private static void assertNoError(List<OperationResult> results, String description) {
        if (results == null) {
            throw new CloudRuntimeException("OVSDB transact returned no result for " + description);
        }
        List<String> errors = new ArrayList<>();
        for (OperationResult r : results) {
            if (r != null && r.getError() != null) {
                errors.add(r.getError() + ": " + r.getDetails());
            }
        }
        if (!errors.isEmpty()) {
            throw new CloudRuntimeException(String.format("OVSDB %s failed: %s", description, String.join("; ", errors)));
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (tcpConnectionService != null) {
            try { tcpConnectionService.close(); } catch (Exception ignored) { }
            tcpConnectionService = null;
        }
        if (bootstrapFactory != null) {
            try { bootstrapFactory.close(); } catch (Exception ignored) { }
            bootstrapFactory = null;
        }
    }

    @FunctionalInterface
    private interface NbAction<T> {
        T call(OvsdbClient client) throws Exception;
    }

    private <T> T runOn(String nbConnection, String caCertPath, String clientCertPath, String clientPrivateKeyPath,
                        NbAction<T> action) {
        Endpoint ep = parse(nbConnection);
        if (ep.scheme == Scheme.UNIX) {
            throw new CloudRuntimeException("Unix-socket OVN connections are not supported by the management server client; use tcp: or ssl:");
        }

        OvsdbConnectionService service = null;
        OvsdbClient client = null;
        boolean closeServiceWhenDone = false;
        try {
            InetAddress addr = InetAddress.getByName(ep.host);
            if (ep.scheme == Scheme.SSL) {
                ICertificateManager cm = OvnSslContext.fromPaths(caCertPath, clientCertPath, clientPrivateKeyPath).asCertificateManager();
                service = new OvsdbConnectionService(bootstrapFactory(), cm);
                closeServiceWhenDone = true;
                client = service.connectWithSsl(addr, ep.port, cm);
            } else {
                service = tcpService();
                client = service.connect(addr, ep.port);
            }
            if (client == null) {
                throw new CloudRuntimeException(String.format("OVN NB at %s did not accept the connection", nbConnection));
            }
            return action.call(client);
        } catch (CloudRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CloudRuntimeException("OVN NB operation against " + nbConnection + " failed: " + e.getMessage(), e);
        } finally {
            if (client != null && service != null) {
                try { service.disconnect(client); } catch (Exception ignored) { }
            }
            if (closeServiceWhenDone && service != null) {
                try { service.close(); } catch (Exception ignored) { }
            }
        }
    }

    private synchronized NettyBootstrapFactoryImpl bootstrapFactory() {
        if (bootstrapFactory == null) {
            bootstrapFactory = new NettyBootstrapFactoryImpl();
        }
        return bootstrapFactory;
    }

    private synchronized OvsdbConnectionService tcpService() {
        if (tcpConnectionService == null) {
            tcpConnectionService = new OvsdbConnectionService(bootstrapFactory(), NOOP_CERT_MANAGER);
        }
        return tcpConnectionService;
    }

    static Endpoint parse(String connection) {
        if (StringUtils.isBlank(connection)) {
            throw new CloudRuntimeException("OVN connection string is blank");
        }
        if (connection.startsWith("unix:/")) {
            return new Endpoint(Scheme.UNIX, connection.substring("unix:".length()), 0);
        }
        Matcher m = CONN_PATTERN.matcher(connection);
        if (!m.matches()) {
            throw new CloudRuntimeException("Invalid OVN connection string: " + connection);
        }
        Scheme scheme = "ssl".equals(m.group(1)) ? Scheme.SSL : Scheme.TCP;
        return new Endpoint(scheme, m.group(2), Integer.parseInt(m.group(3)));
    }

    enum Scheme { TCP, SSL, UNIX }

    static final class Endpoint {
        final Scheme scheme;
        final String host;
        final int port;

        Endpoint(Scheme scheme, String host, int port) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
        }
    }

    /**
     * The OvsdbConnectionService constructor requires a non-null ICertificateManager even for plain
     * TCP. None of its methods are invoked along the TCP code path.
     */
    private static final class NoopCertificateManager implements ICertificateManager {
        @Override public KeyStore getODLKeyStore() { return null; }
        @Override public KeyStore getTrustKeyStore() { return null; }
        @Override public String[] getCipherSuites() { return new String[0]; }
        @Override public String[] getTlsProtocols() { return new String[0]; }
        @Override public String getCertificateTrustStore(String s, String d, boolean p) { return null; }
        @Override public String getODLKeyStoreCertificate(String s, boolean p) { return null; }
        @Override public String genODLKeyStoreCertificateReq(String s, boolean p) { return null; }
        @Override public SSLContext getServerContext() { return null; }
        @Override public boolean importSslDataKeystores(String a, String b, String c, String d, String e, String[] f, String g) { return false; }
        @Override public void exportSslDataKeystores() { }
    }
}
