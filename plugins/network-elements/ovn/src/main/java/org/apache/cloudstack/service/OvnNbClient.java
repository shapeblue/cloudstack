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
    private static final String SOUTHBOUND_DB = "OVN_Southbound";
    private static final String LOGICAL_SWITCH_TABLE = "Logical_Switch";
    private static final String LOGICAL_SWITCH_PORT_TABLE = "Logical_Switch_Port";
    private static final String LOGICAL_ROUTER_TABLE = "Logical_Router";
    private static final String LOGICAL_ROUTER_PORT_TABLE = "Logical_Router_Port";
    private static final String LOGICAL_ROUTER_STATIC_ROUTE_TABLE = "Logical_Router_Static_Route";
    private static final String GATEWAY_CHASSIS_TABLE = "Gateway_Chassis";
    private static final String DHCP_OPTIONS_TABLE = "DHCP_Options";
    private static final String NAT_TABLE = "NAT";
    private static final String ACL_TABLE = "ACL";
    private static final String LOAD_BALANCER_TABLE = "Load_Balancer";
    private static final String LOAD_BALANCER_HEALTH_CHECK_TABLE = "Load_Balancer_Health_Check";
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

    /**
     * Merges the supplied entries into the {@code options} column of an existing Logical_Switch_Port.
     * Existing keys not in {@code optionsToSet} are preserved; keys in {@code optionsToSet} are
     * overwritten. Use this to set values like {@code nat-addresses="<MAC> <IP> ..."} on the
     * gateway-side LSP so ovn-controller emits gratuitous ARPs for SNAT/FIP addresses on claim.
     */
    public void setLspOptions(String nbConnection, String caCertPath, String clientCertPath,
                              String clientPrivateKeyPath,
                              String lspName, Map<String, String> optionsToSet) {
        if (StringUtils.isBlank(lspName)) {
            throw new CloudRuntimeException("LSP name is blank");
        }
        if (optionsToSet == null || optionsToSet.isEmpty()) {
            return;
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lspTable = schema.table(LOGICAL_SWITCH_PORT_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lspNameCol = lspTable.column("name", String.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> optsCol = lspTable.column("options", Map.class);

            Operation<GenericTableSchema> select = OVSDB_OPS.select(lspTable)
                    .column(optsCol).where(lspNameCol.opEqual(lspName)).build();
            List<OperationResult> selResult = client.transact(schema, Collections.<Operation>singletonList(select))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (selResult == null || selResult.isEmpty() || selResult.get(0).getRows() == null
                    || selResult.get(0).getRows().isEmpty()) {
                throw new CloudRuntimeException("LSP " + lspName + " not found while setting options");
            }
            @SuppressWarnings("unchecked")
            Map<String, String> existing = (Map<String, String>) selResult.get(0).getRows().get(0)
                    .getColumn(optsCol).getData();
            Map<String, String> merged = new HashMap<>();
            if (existing != null) merged.putAll(existing);
            merged.putAll(optionsToSet);

            // Bail out when nothing actually changes - avoids spurious NB notifications that
            // ripple to ovn-controller and cause unnecessary recomputes.
            if (existing != null && existing.equals(merged)) {
                logger.debug("LSP [{}] options already at desired state - skipping update", lspName);
                return null;
            }

            Operation<GenericTableSchema> update = OVSDB_OPS.update(lspTable)
                    .set(optsCol, merged).where(lspNameCol.opEqual(lspName)).build();
            List<OperationResult> results = client.transact(schema, Collections.singletonList(update))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("set options on LSP %s", lspName));
            logger.info("Set options [{}] on Logical_Switch_Port [{}] at {}", optionsToSet, lspName, nbConnection);
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

    /**
     * Idempotently creates a Logical_Router with the given name and external_ids.
     */
    public void createLogicalRouter(String nbConnection, String caCertPath, String clientCertPath,
                                    String clientPrivateKeyPath, String routerName, Map<String, String> externalIds) {
        if (StringUtils.isBlank(routerName)) {
            throw new CloudRuntimeException("Logical_Router name is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lr = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> nameCol = lr.column("name", String.class);
            if (rowExistsByName(client, schema, lr, nameCol, routerName)) {
                logger.debug("Logical_Router [{}] already exists on {} - skipping create", routerName, nbConnection);
                return null;
            }
            Insert<GenericTableSchema> insert = OVSDB_OPS.insert(lr).value(nameCol, routerName);
            if (externalIds != null && !externalIds.isEmpty()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                ColumnSchema<GenericTableSchema, Map> extIdsCol = lr.column("external_ids", Map.class);
                insert = insert.value(extIdsCol, new HashMap<>(externalIds));
            }
            List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(insert))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("create Logical_Router %s", routerName));
            logger.info("Created OVN Logical_Router [{}] at {}", routerName, nbConnection);
            return null;
        });
    }

    /**
     * Removes a Logical_Router by name. Idempotent. Caller is responsible for first detaching any
     * router ports the LR owns and for clearing nat rules.
     */
    public void deleteLogicalRouter(String nbConnection, String caCertPath, String clientCertPath,
                                    String clientPrivateKeyPath, String routerName) {
        if (StringUtils.isBlank(routerName)) {
            throw new CloudRuntimeException("Logical_Router name is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lr = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> nameCol = lr.column("name", String.class);
            if (!rowExistsByName(client, schema, lr, nameCol, routerName)) {
                logger.debug("Logical_Router [{}] not present on {} - nothing to delete", routerName, nbConnection);
                return null;
            }
            Operation<GenericTableSchema> delete = OVSDB_OPS.delete(lr).where(nameCol.opEqual(routerName)).build();
            List<OperationResult> results = client.transact(schema, Collections.singletonList(delete))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("delete Logical_Router %s", routerName));
            logger.info("Deleted OVN Logical_Router [{}] at {}", routerName, nbConnection);
            return null;
        });
    }

    /**
     * Idempotently attaches a routed segment to a Logical_Router: creates a Logical_Router_Port
     * with the given mac/networks and a peer Logical_Switch_Port of type=router on the Logical_Switch
     * pointing back at it. Used to wire the LR to either the guest tier or the public/localnet tier.
     */
    public void attachRouterToSwitch(String nbConnection, String caCertPath, String clientCertPath,
                                     String clientPrivateKeyPath,
                                     String routerName, String switchName,
                                     String lrpName, String lrpMac, List<String> lrpNetworks) {
        if (StringUtils.isBlank(routerName) || StringUtils.isBlank(switchName) || StringUtils.isBlank(lrpName)) {
            throw new CloudRuntimeException("Logical_Router/Switch/Port name is blank");
        }
        if (StringUtils.isBlank(lrpMac) || lrpNetworks == null || lrpNetworks.isEmpty()) {
            throw new CloudRuntimeException("Logical_Router_Port mac/networks are required");
        }
        final String lspName = "lsp-" + lrpName;
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lrTable = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            GenericTableSchema lrpTable = schema.table(LOGICAL_ROUTER_PORT_TABLE, GenericTableSchema.class);
            GenericTableSchema lsTable = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            GenericTableSchema lspTable = schema.table(LOGICAL_SWITCH_PORT_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lrNameCol = lrTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> lrpNameCol = lrpTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> lsNameCol = lsTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> lspNameCol = lspTable.column("name", String.class);

            boolean lrpExists = rowExistsByName(client, schema, lrpTable, lrpNameCol, lrpName);
            boolean lspExists = rowExistsByName(client, schema, lspTable, lspNameCol, lspName);
            if (lrpExists && lspExists) {
                logger.debug("Router attachment {} ↔ {} already in place - skipping", lrpName, lspName);
                return null;
            }

            List<Operation> ops = new ArrayList<>();
            UUID lrpRef = null;
            if (!lrpExists) {
                ColumnSchema<GenericTableSchema, String> lrpMacCol = lrpTable.column("mac", String.class);
                ColumnSchema<GenericTableSchema, Set<String>> lrpNetCol = lrpTable.multiValuedColumn("networks", String.class);
                Insert<GenericTableSchema> insertLrp = OVSDB_OPS.insert(lrpTable)
                        .withId("newlrp")
                        .value(lrpNameCol, lrpName)
                        .value(lrpMacCol, lrpMac)
                        .value(lrpNetCol, new java.util.HashSet<>(lrpNetworks));
                ops.add(insertLrp);
                ColumnSchema<GenericTableSchema, Set<UUID>> lrPortsCol = lrTable.multiValuedColumn("ports", UUID.class);
                lrpRef = new UUID("newlrp");
                ops.add(OVSDB_OPS.mutate(lrTable)
                        .addMutation(lrPortsCol, Mutator.INSERT, Collections.singleton(lrpRef))
                        .where(lrNameCol.opEqual(routerName)).build());
            }
            if (!lspExists) {
                ColumnSchema<GenericTableSchema, String> lspTypeCol = lspTable.column("type", String.class);
                ColumnSchema<GenericTableSchema, Set<String>> lspAddrCol = lspTable.multiValuedColumn("addresses", String.class);
                @SuppressWarnings({"rawtypes", "unchecked"})
                ColumnSchema<GenericTableSchema, Map> lspOptsCol = lspTable.column("options", Map.class);
                Map<String, String> opts = new HashMap<>();
                opts.put("router-port", lrpName);
                Insert<GenericTableSchema> insertLsp = OVSDB_OPS.insert(lspTable)
                        .withId("newlsp")
                        .value(lspNameCol, lspName)
                        .value(lspTypeCol, "router")
                        .value(lspAddrCol, Collections.singleton("router"))
                        .value(lspOptsCol, opts);
                ops.add(insertLsp);
                ColumnSchema<GenericTableSchema, Set<UUID>> lsPortsCol = lsTable.multiValuedColumn("ports", UUID.class);
                ops.add(OVSDB_OPS.mutate(lsTable)
                        .addMutation(lsPortsCol, Mutator.INSERT, Collections.singleton(new UUID("newlsp")))
                        .where(lsNameCol.opEqual(switchName)).build());
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("attach Logical_Router %s to Logical_Switch %s via %s", routerName, switchName, lrpName));
            logger.info("Attached OVN Logical_Router [{}] to Logical_Switch [{}] via LRP [{}] at {}",
                    routerName, switchName, lrpName, nbConnection);
            return null;
        });
    }

    /**
     * Idempotently adds a localnet Logical_Switch_Port to a Logical_Switch so traffic can egress
     * the OVN integration bridge through ovn-bridge-mappings to the named physical network.
     */
    public void addLocalnetPort(String nbConnection, String caCertPath, String clientCertPath,
                                String clientPrivateKeyPath,
                                String switchName, String lspName, String physicalNetworkName, Integer vlanTag) {
        if (StringUtils.isBlank(switchName) || StringUtils.isBlank(lspName) || StringUtils.isBlank(physicalNetworkName)) {
            throw new CloudRuntimeException("Localnet port arguments are incomplete");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lsTable = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            GenericTableSchema lspTable = schema.table(LOGICAL_SWITCH_PORT_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lsNameCol = lsTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> lspNameCol = lspTable.column("name", String.class);
            if (rowExistsByName(client, schema, lspTable, lspNameCol, lspName)) {
                logger.debug("Localnet LSP [{}] already exists - skipping", lspName);
                return null;
            }
            ColumnSchema<GenericTableSchema, String> typeCol = lspTable.column("type", String.class);
            ColumnSchema<GenericTableSchema, Set<String>> addrCol = lspTable.multiValuedColumn("addresses", String.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> optsCol = lspTable.column("options", Map.class);
            Map<String, String> opts = new HashMap<>();
            opts.put("network_name", physicalNetworkName);
            ColumnSchema<GenericTableSchema, Set<UUID>> lsPortsCol = lsTable.multiValuedColumn("ports", UUID.class);
            Insert<GenericTableSchema> insertLsp = OVSDB_OPS.insert(lspTable)
                    .withId("newln")
                    .value(lspNameCol, lspName)
                    .value(typeCol, "localnet")
                    .value(addrCol, Collections.singleton("unknown"))
                    .value(optsCol, opts);
            if (vlanTag != null) {
                ColumnSchema<GenericTableSchema, Set<Long>> tagCol = lspTable.multiValuedColumn("tag", Long.class);
                insertLsp = insertLsp.value(tagCol, Collections.singleton((long) vlanTag));
            }
            List<Operation> ops = new ArrayList<>();
            ops.add(insertLsp);
            ops.add(OVSDB_OPS.mutate(lsTable)
                    .addMutation(lsPortsCol, Mutator.INSERT, Collections.singleton(new UUID("newln")))
                    .where(lsNameCol.opEqual(switchName)).build());
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("add localnet LSP %s on %s", lspName, switchName));
            logger.info("Added OVN localnet Logical_Switch_Port [{}] on Logical_Switch [{}] (network_name={}, vlan={}) at {}",
                    lspName, switchName, physicalNetworkName, vlanTag, nbConnection);
            return null;
        });
    }

    /**
     * Idempotently adds a NAT rule to a Logical_Router. {@code natType} should be {@code snat},
     * {@code dnat} or {@code dnat_and_snat}. Setting both {@code distributedMac} and
     * {@code distributedLogicalPort} marks the row as distributed-NAT (so ovn-northd can apply
     * the rule on the chassis hosting the workload, no Gateway_Chassis required).
     */
    public void addNatRule(String nbConnection, String caCertPath, String clientCertPath,
                           String clientPrivateKeyPath,
                           String routerName, String natType, String externalIp, String logicalIp,
                           Map<String, String> externalIds) {
        addNatRule(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath,
                routerName, natType, externalIp, logicalIp, externalIds, null, null);
    }

    public void addNatRule(String nbConnection, String caCertPath, String clientCertPath,
                           String clientPrivateKeyPath,
                           String routerName, String natType, String externalIp, String logicalIp,
                           Map<String, String> externalIds,
                           String distributedMac, String distributedLogicalPort) {
        if (StringUtils.isBlank(routerName) || StringUtils.isBlank(natType)
                || StringUtils.isBlank(externalIp) || StringUtils.isBlank(logicalIp)) {
            throw new CloudRuntimeException("NAT rule arguments are incomplete");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lrTable = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            GenericTableSchema natTable = schema.table(NAT_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lrNameCol = lrTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> natTypeCol = natTable.column("type", String.class);
            ColumnSchema<GenericTableSchema, String> natExtCol = natTable.column("external_ip", String.class);
            ColumnSchema<GenericTableSchema, String> natLogCol = natTable.column("logical_ip", String.class);

            if (natRuleExists(client, schema, natTable, natType, externalIp, logicalIp)) {
                logger.debug("NAT [{} {}→{}] on {} already exists - skipping", natType, logicalIp, externalIp, routerName);
                return null;
            }

            Insert<GenericTableSchema> insertNat = OVSDB_OPS.insert(natTable)
                    .withId("newnat")
                    .value(natTypeCol, natType)
                    .value(natExtCol, externalIp)
                    .value(natLogCol, logicalIp);
            if (externalIds != null && !externalIds.isEmpty()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                ColumnSchema<GenericTableSchema, Map> extIdsCol = natTable.column("external_ids", Map.class);
                insertNat = insertNat.value(extIdsCol, new HashMap<>(externalIds));
            }
            if (StringUtils.isNotBlank(distributedMac)) {
                ColumnSchema<GenericTableSchema, String> extMacCol = natTable.column("external_mac", String.class);
                insertNat = insertNat.value(extMacCol, distributedMac);
            }
            if (StringUtils.isNotBlank(distributedLogicalPort)) {
                ColumnSchema<GenericTableSchema, String> logPortCol = natTable.column("logical_port", String.class);
                insertNat = insertNat.value(logPortCol, distributedLogicalPort);
            }
            ColumnSchema<GenericTableSchema, Set<UUID>> lrNatCol = lrTable.multiValuedColumn("nat", UUID.class);
            List<Operation> ops = new ArrayList<>();
            ops.add(insertNat);
            ops.add(OVSDB_OPS.mutate(lrTable)
                    .addMutation(lrNatCol, Mutator.INSERT, Collections.singleton(new UUID("newnat")))
                    .where(lrNameCol.opEqual(routerName)).build());
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("add NAT %s %s→%s on %s", natType, logicalIp, externalIp, routerName));
            logger.info("Added OVN NAT [{} {} → {}] on Logical_Router [{}] at {}", natType, logicalIp, externalIp, routerName, nbConnection);
            return null;
        });
    }

    /**
     * Idempotently adds a port-specific NAT rule (DNAT) to a Logical_Router. The rule matches
     * traffic arriving at {@code externalIp:externalPort/protocol} and DNATs it to
     * {@code logicalIp:externalPort} (OVN translates destination port to the same value).
     * Setting {@code distributedMac} and {@code distributedLogicalPort} marks the row as
     * distributed so ovn-northd applies DNAT on the workload chassis without the gateway.
     */
    public void addNatRuleWithPorts(String nbConnection, String caCertPath, String clientCertPath,
                                    String clientPrivateKeyPath,
                                    String routerName, String natType, String externalIp,
                                    int externalPort, String protocol, String logicalIp,
                                    Map<String, String> externalIds,
                                    String distributedMac, String distributedLogicalPort) {
        if (StringUtils.isBlank(routerName) || StringUtils.isBlank(natType)
                || StringUtils.isBlank(externalIp) || StringUtils.isBlank(logicalIp)
                || StringUtils.isBlank(protocol)) {
            throw new CloudRuntimeException("addNatRuleWithPorts: arguments are incomplete");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lrTable = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            GenericTableSchema natTable = schema.table(NAT_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lrNameCol = lrTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> natTypeCol = natTable.column("type", String.class);
            ColumnSchema<GenericTableSchema, String> natExtCol = natTable.column("external_ip", String.class);
            ColumnSchema<GenericTableSchema, String> natLogCol = natTable.column("logical_ip", String.class);
            ColumnSchema<GenericTableSchema, Set<Long>> natExtPortCol = natTable.multiValuedColumn("external_port", Long.class);
            ColumnSchema<GenericTableSchema, Set<String>> natProtoCol = natTable.multiValuedColumn("protocol", String.class);

            if (natRuleWithPortExists(client, schema, natTable, natType, externalIp, externalPort, protocol)) {
                logger.debug("NAT [{} {}:{}/{}→{}] on {} already exists - skipping",
                        natType, externalIp, externalPort, protocol, logicalIp, routerName);
                return null;
            }

            Insert<GenericTableSchema> insertNat = OVSDB_OPS.insert(natTable)
                    .withId("newnat")
                    .value(natTypeCol, natType)
                    .value(natExtCol, externalIp)
                    .value(natLogCol, logicalIp)
                    .value(natExtPortCol, Collections.singleton((long) externalPort))
                    .value(natProtoCol, Collections.singleton(protocol));
            if (externalIds != null && !externalIds.isEmpty()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                ColumnSchema<GenericTableSchema, Map> extIdsCol = natTable.column("external_ids", Map.class);
                insertNat = insertNat.value(extIdsCol, new HashMap<>(externalIds));
            }
            if (StringUtils.isNotBlank(distributedMac)) {
                ColumnSchema<GenericTableSchema, String> extMacCol = natTable.column("external_mac", String.class);
                insertNat = insertNat.value(extMacCol, distributedMac);
            }
            if (StringUtils.isNotBlank(distributedLogicalPort)) {
                ColumnSchema<GenericTableSchema, String> logPortCol = natTable.column("logical_port", String.class);
                insertNat = insertNat.value(logPortCol, distributedLogicalPort);
            }
            ColumnSchema<GenericTableSchema, Set<UUID>> lrNatCol = lrTable.multiValuedColumn("nat", UUID.class);
            List<Operation> ops = new ArrayList<>();
            ops.add(insertNat);
            ops.add(OVSDB_OPS.mutate(lrTable)
                    .addMutation(lrNatCol, Mutator.INSERT, Collections.singleton(new UUID("newnat")))
                    .where(lrNameCol.opEqual(routerName)).build());
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("add NAT %s %s:%d/%s→%s on %s",
                    natType, externalIp, externalPort, protocol, logicalIp, routerName));
            logger.info("Added OVN port NAT [{} {}:{}/{} → {}] on Logical_Router [{}] at {}",
                    natType, externalIp, externalPort, protocol, logicalIp, routerName, nbConnection);
            return null;
        });
    }

    /**
     * Removes every NAT row matching {@code type + externalIp + externalPort + protocol} from
     * the Logical_Router. Idempotent: no-op if nothing matches.
     */
    public void removeNatRulesByExternalIpAndPort(String nbConnection, String caCertPath, String clientCertPath,
                                                  String clientPrivateKeyPath,
                                                  String routerName, String natType,
                                                  String externalIp, int externalPort, String protocol) {
        if (StringUtils.isBlank(routerName) || StringUtils.isBlank(natType)
                || StringUtils.isBlank(externalIp) || StringUtils.isBlank(protocol)) {
            throw new CloudRuntimeException("removeNatRulesByExternalIpAndPort: arguments are incomplete");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema natTable = schema.table(NAT_TABLE, GenericTableSchema.class);
            GenericTableSchema lrTable = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> natTypeCol = natTable.column("type", String.class);
            ColumnSchema<GenericTableSchema, String> natExtCol = natTable.column("external_ip", String.class);
            ColumnSchema<GenericTableSchema, UUID> natUuidCol = natTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, Set<Long>> natExtPortCol = natTable.multiValuedColumn("external_port", Long.class);
            ColumnSchema<GenericTableSchema, Set<String>> natProtoCol = natTable.multiValuedColumn("protocol", String.class);
            ColumnSchema<GenericTableSchema, String> lrNameCol = lrTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lrNatCol = lrTable.multiValuedColumn("nat", UUID.class);

            // Select all rows matching type+externalIp, then filter by port+protocol client-side
            // because OVSDB conditions cannot match inside set columns.
            Operation<GenericTableSchema> sel = OVSDB_OPS.select(natTable)
                    .column(natUuidCol).column(natExtPortCol).column(natProtoCol)
                    .where(natTypeCol.opEqual(natType))
                    .and(natExtCol.opEqual(externalIp)).build();
            List<OperationResult> selResult = client.transact(schema, Collections.<Operation>singletonList(sel))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (selResult == null || selResult.isEmpty() || selResult.get(0).getRows() == null) {
                return null;
            }
            List<UUID> uuids = new ArrayList<>();
            for (Row<GenericTableSchema> row : selResult.get(0).getRows()) {
                @SuppressWarnings("unchecked")
                Set<Long> ports = (Set<Long>) row.getColumn(natExtPortCol).getData();
                @SuppressWarnings("unchecked")
                Set<String> protos = (Set<String>) row.getColumn(natProtoCol).getData();
                if (ports != null && ports.contains((long) externalPort)
                        && protos != null && protos.contains(protocol)) {
                    uuids.add(row.getColumn(natUuidCol).getData());
                }
            }
            if (uuids.isEmpty()) {
                return null;
            }
            List<Operation> ops = new ArrayList<>();
            ops.add(OVSDB_OPS.mutate(lrTable)
                    .addMutation(lrNatCol, Mutator.DELETE, new java.util.HashSet<>(uuids))
                    .where(lrNameCol.opEqual(routerName)).build());
            for (UUID u : uuids) {
                ops.add(OVSDB_OPS.delete(natTable).where(natUuidCol.opEqual(u)).build());
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("remove NAT %s %s:%d/%s on %s",
                    natType, externalIp, externalPort, protocol, routerName));
            logger.info("Removed {} OVN port NAT row(s) [{} {}:{}/{}] on Logical_Router [{}] at {}",
                    uuids.size(), natType, externalIp, externalPort, protocol, routerName, nbConnection);
            return null;
        });
    }

    private boolean natRuleWithPortExists(OvsdbClient client, DatabaseSchema schema, GenericTableSchema natTable,
                                          String natType, String externalIp, int externalPort,
                                          String protocol) throws Exception {
        ColumnSchema<GenericTableSchema, String> natTypeCol = natTable.column("type", String.class);
        ColumnSchema<GenericTableSchema, String> natExtCol = natTable.column("external_ip", String.class);
        ColumnSchema<GenericTableSchema, Set<Long>> natExtPortCol = natTable.multiValuedColumn("external_port", Long.class);
        ColumnSchema<GenericTableSchema, Set<String>> natProtoCol = natTable.multiValuedColumn("protocol", String.class);
        ColumnSchema<GenericTableSchema, UUID> uuidCol = natTable.column("_uuid", UUID.class);
        Operation<GenericTableSchema> sel = OVSDB_OPS.select(natTable)
                .column(uuidCol).column(natExtPortCol).column(natProtoCol)
                .where(natTypeCol.opEqual(natType))
                .and(natExtCol.opEqual(externalIp)).build();
        List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(sel))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        if (results == null || results.isEmpty() || results.get(0).getRows() == null) {
            return false;
        }
        for (Row<GenericTableSchema> row : results.get(0).getRows()) {
            @SuppressWarnings("unchecked")
            Set<Long> ports = (Set<Long>) row.getColumn(natExtPortCol).getData();
            @SuppressWarnings("unchecked")
            Set<String> protos = (Set<String>) row.getColumn(natProtoCol).getData();
            if (ports != null && ports.contains((long) externalPort)
                    && protos != null && protos.contains(protocol)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes every NAT rule matching {@code type}+{@code external_ip} from the Logical_Router,
     * regardless of logical_ip. Convenient when reverting a static NAT mapping where the caller
     * does not know the previously-bound private address. Idempotent.
     */
    public void removeNatRulesByExternalIp(String nbConnection, String caCertPath, String clientCertPath,
                                           String clientPrivateKeyPath,
                                           String routerName, String natType, String externalIp) {
        if (StringUtils.isBlank(routerName) || StringUtils.isBlank(natType) || StringUtils.isBlank(externalIp)) {
            throw new CloudRuntimeException("removeNatRulesByExternalIp: arguments are incomplete");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema natTable = schema.table(NAT_TABLE, GenericTableSchema.class);
            GenericTableSchema lrTable = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> natTypeCol = natTable.column("type", String.class);
            ColumnSchema<GenericTableSchema, String> natExtCol = natTable.column("external_ip", String.class);
            ColumnSchema<GenericTableSchema, UUID> natUuidCol = natTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, String> lrNameCol = lrTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lrNatCol = lrTable.multiValuedColumn("nat", UUID.class);

            // Logical_Router.nat is a strong reference set; deleting NAT rows without first
            // mutating the LR.nat column triggers a referential-integrity violation. Resolve
            // the UUIDs to remove via select, then mutate-and-delete in one transaction.
            Operation<GenericTableSchema> selectUuids = OVSDB_OPS.select(natTable).column(natUuidCol)
                    .where(natTypeCol.opEqual(natType))
                    .and(natExtCol.opEqual(externalIp)).build();
            List<OperationResult> selectResult = client.transact(schema, Collections.<Operation>singletonList(selectUuids))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (selectResult == null || selectResult.isEmpty() || selectResult.get(0).getRows() == null
                    || selectResult.get(0).getRows().isEmpty()) {
                logger.debug("No NAT rows match type={} ext={} on {} - nothing to remove", natType, externalIp, routerName);
                return null;
            }
            List<UUID> uuids = new ArrayList<>();
            for (Row<GenericTableSchema> row : selectResult.get(0).getRows()) {
                uuids.add(row.getColumn(natUuidCol).getData());
            }
            List<Operation> ops = new ArrayList<>();
            ops.add(OVSDB_OPS.mutate(lrTable)
                    .addMutation(lrNatCol, Mutator.DELETE, new java.util.HashSet<>(uuids))
                    .where(lrNameCol.opEqual(routerName)).build());
            for (UUID u : uuids) {
                ops.add(OVSDB_OPS.delete(natTable).where(natUuidCol.opEqual(u)).build());
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("delete NAT %s ext=%s on %s", natType, externalIp, routerName));
            logger.info("Deleted {} OVN NAT row(s) [{} ext={}] on Logical_Router [{}] at {}",
                    uuids.size(), natType, externalIp, routerName, nbConnection);
            return null;
        });
    }

    /**
     * Removes a NAT rule matching type/external_ip/logical_ip from a Logical_Router. Idempotent.
     */
    public void removeNatRule(String nbConnection, String caCertPath, String clientCertPath,
                              String clientPrivateKeyPath,
                              String routerName, String natType, String externalIp, String logicalIp) {
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema natTable = schema.table(NAT_TABLE, GenericTableSchema.class);
            GenericTableSchema lrTable = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> natTypeCol = natTable.column("type", String.class);
            ColumnSchema<GenericTableSchema, String> natExtCol = natTable.column("external_ip", String.class);
            ColumnSchema<GenericTableSchema, String> natLogCol = natTable.column("logical_ip", String.class);
            ColumnSchema<GenericTableSchema, UUID> natUuidCol = natTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, String> lrNameCol = lrTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lrNatCol = lrTable.multiValuedColumn("nat", UUID.class);

            Operation<GenericTableSchema> selectUuids = OVSDB_OPS.select(natTable).column(natUuidCol)
                    .where(natTypeCol.opEqual(natType))
                    .and(natExtCol.opEqual(externalIp))
                    .and(natLogCol.opEqual(logicalIp)).build();
            List<OperationResult> selectResult = client.transact(schema, Collections.<Operation>singletonList(selectUuids))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (selectResult == null || selectResult.isEmpty() || selectResult.get(0).getRows() == null
                    || selectResult.get(0).getRows().isEmpty()) {
                return null;
            }
            List<UUID> uuids = new ArrayList<>();
            for (Row<GenericTableSchema> row : selectResult.get(0).getRows()) {
                uuids.add(row.getColumn(natUuidCol).getData());
            }
            List<Operation> ops = new ArrayList<>();
            ops.add(OVSDB_OPS.mutate(lrTable)
                    .addMutation(lrNatCol, Mutator.DELETE, new java.util.HashSet<>(uuids))
                    .where(lrNameCol.opEqual(routerName)).build());
            for (UUID u : uuids) {
                ops.add(OVSDB_OPS.delete(natTable).where(natUuidCol.opEqual(u)).build());
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("delete NAT %s %s→%s on %s", natType, logicalIp, externalIp, routerName));
            logger.info("Deleted OVN NAT [{} {} → {}] on Logical_Router [{}] at {}", natType, logicalIp, externalIp, routerName, nbConnection);
            return null;
        });
    }

    /**
     * Lists the chassis system-ids registered in the OVN_Southbound database. Useful for picking
     * a deterministic anchor chassis for a Logical_Router gateway port without having to map
     * CloudStack hostnames onto OVS system-ids.
     */
    public List<String> listSouthboundChassisNames(String sbConnection, String caCertPath, String clientCertPath,
                                                   String clientPrivateKeyPath) {
        return runOnDb(sbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, SOUTHBOUND_DB, client -> {
            DatabaseSchema schema = client.getSchema(SOUTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema chassisTable = schema.table("Chassis", GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> nameCol = chassisTable.column("name", String.class);
            Operation<GenericTableSchema> select = OVSDB_OPS.select(chassisTable).column(nameCol);
            List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(select))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            List<String> names = new ArrayList<>();
            if (results != null && !results.isEmpty() && results.get(0).getRows() != null) {
                for (Row<GenericTableSchema> row : results.get(0).getRows()) {
                    String n = row.getColumn(nameCol).getData();
                    if (n != null && !n.isEmpty()) names.add(n);
                }
            }
            return names;
        });
    }

    /**
     * Removes any Gateway_Chassis row attached to {@code lrpName} whose {@code chassis_name} is
     * not present in {@code liveChassisNames}. Used to clean up after a host is re-added to the
     * zone with a fresh OVS system-id - the old Gateway_Chassis row would otherwise keep pointing
     * to a chassis that no longer exists in SB, so ovn-northd never claims the cr-lrp port and
     * the SNAT/DNAT pipeline stays unmaterialised. Returns the number of rows pruned.
     */
    public int pruneStaleGatewayChassis(String nbConnection, String caCertPath, String clientCertPath,
                                         String clientPrivateKeyPath,
                                         String lrpName, Set<String> liveChassisNames) {
        if (StringUtils.isBlank(lrpName) || liveChassisNames == null) {
            throw new CloudRuntimeException("pruneStaleGatewayChassis: arguments are incomplete");
        }
        return runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lrpTable = schema.table(LOGICAL_ROUTER_PORT_TABLE, GenericTableSchema.class);
            GenericTableSchema gcTable = schema.table(GATEWAY_CHASSIS_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lrpNameCol = lrpTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lrpGcCol = lrpTable.multiValuedColumn("gateway_chassis", UUID.class);
            ColumnSchema<GenericTableSchema, UUID> gcUuidCol = gcTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, String> gcChassisCol = gcTable.column("chassis_name", String.class);

            // Get the GC UUIDs currently bound to this LRP.
            Operation<GenericTableSchema> selLrp = OVSDB_OPS.select(lrpTable).column(lrpGcCol)
                    .where(lrpNameCol.opEqual(lrpName)).build();
            List<OperationResult> lrpResult = client.transact(schema, Collections.<Operation>singletonList(selLrp))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (lrpResult == null || lrpResult.isEmpty() || lrpResult.get(0).getRows() == null
                    || lrpResult.get(0).getRows().isEmpty()) {
                return 0;
            }
            @SuppressWarnings("unchecked")
            Set<UUID> gcRefs = (Set<UUID>) lrpResult.get(0).getRows().get(0).getColumn(lrpGcCol).getData();
            if (gcRefs == null || gcRefs.isEmpty()) {
                return 0;
            }

            // Inspect each GC row's chassis_name and collect stale ones.
            Set<UUID> stale = new java.util.HashSet<>();
            for (UUID gcUuid : gcRefs) {
                Operation<GenericTableSchema> selGc = OVSDB_OPS.select(gcTable).column(gcChassisCol)
                        .where(gcUuidCol.opEqual(gcUuid)).build();
                List<OperationResult> gcResult = client.transact(schema, Collections.<Operation>singletonList(selGc))
                        .get(timeoutMs, TimeUnit.MILLISECONDS);
                if (gcResult == null || gcResult.isEmpty() || gcResult.get(0).getRows() == null
                        || gcResult.get(0).getRows().isEmpty()) {
                    continue;
                }
                String chassisName = gcResult.get(0).getRows().get(0).getColumn(gcChassisCol).getData();
                if (chassisName == null || !liveChassisNames.contains(chassisName)) {
                    stale.add(gcUuid);
                }
            }
            if (stale.isEmpty()) {
                return 0;
            }

            // Detach from LRP first (strong ref) then delete each GC row.
            List<Operation> ops = new ArrayList<>();
            ops.add(OVSDB_OPS.mutate(lrpTable)
                    .addMutation(lrpGcCol, Mutator.DELETE, stale)
                    .where(lrpNameCol.opEqual(lrpName)).build());
            for (UUID u : stale) {
                ops.add(OVSDB_OPS.delete(gcTable).where(gcUuidCol.opEqual(u)).build());
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("prune stale Gateway_Chassis on LRP %s", lrpName));
            logger.info("Pruned {} stale Gateway_Chassis row(s) from LRP [{}] (live chassis: {})",
                    stale.size(), lrpName, liveChassisNames);
            return stale.size();
        });
    }

    /**
     * Idempotently anchors a Logical_Router_Port to a chassis via Gateway_Chassis. This is what
     * lets ovn-northd materialise the centralised NAT pipeline for the LR (lr_in_dnat,
     * lr_in_unsnat, lr_out_snat) — without it the lr_in_dnat table only carries the default
     * priority-0 rule and DNAT silently does not happen.
     */
    public void setLrpGatewayChassis(String nbConnection, String caCertPath, String clientCertPath,
                                     String clientPrivateKeyPath,
                                     String lrpName, String chassisName, int priority) {
        if (StringUtils.isBlank(lrpName) || StringUtils.isBlank(chassisName)) {
            throw new CloudRuntimeException("Gateway_Chassis arguments are incomplete");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lrpTable = schema.table(LOGICAL_ROUTER_PORT_TABLE, GenericTableSchema.class);
            GenericTableSchema gcTable = schema.table(GATEWAY_CHASSIS_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lrpNameCol = lrpTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> gcChassisCol = gcTable.column("chassis_name", String.class);
            ColumnSchema<GenericTableSchema, Long> gcPrioCol = gcTable.column("priority", Long.class);
            ColumnSchema<GenericTableSchema, String> gcNameCol = gcTable.column("name", String.class);

            Operation<GenericTableSchema> existingSel = OVSDB_OPS.select(gcTable).column(gcChassisCol)
                    .where(gcChassisCol.opEqual(chassisName))
                    .and(gcNameCol.opEqual(lrpName + "_" + chassisName)).build();
            List<OperationResult> existing = client.transact(schema, Collections.<Operation>singletonList(existingSel))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (existing != null && !existing.isEmpty()
                    && existing.get(0).getRows() != null && !existing.get(0).getRows().isEmpty()) {
                logger.debug("Gateway_Chassis for LRP [{}] on [{}] already exists - skipping", lrpName, chassisName);
                return null;
            }

            Insert<GenericTableSchema> insertGc = OVSDB_OPS.insert(gcTable)
                    .withId("newgc")
                    .value(gcNameCol, lrpName + "_" + chassisName)
                    .value(gcChassisCol, chassisName)
                    .value(gcPrioCol, (long) priority);
            ColumnSchema<GenericTableSchema, Set<UUID>> lrpGcCol = lrpTable.multiValuedColumn("gateway_chassis", UUID.class);
            List<Operation> ops = new ArrayList<>();
            ops.add(insertGc);
            ops.add(OVSDB_OPS.mutate(lrpTable)
                    .addMutation(lrpGcCol, Mutator.INSERT, Collections.singleton(new UUID("newgc")))
                    .where(lrpNameCol.opEqual(lrpName)).build());
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("set Gateway_Chassis %s on LRP %s", chassisName, lrpName));
            logger.info("Set OVN Gateway_Chassis [{} prio={}] on Logical_Router_Port [{}] at {}",
                    chassisName, priority, lrpName, nbConnection);
            return null;
        });
    }

    /**
     * Idempotently adds a static route on a Logical_Router.
     */
    public void addStaticRoute(String nbConnection, String caCertPath, String clientCertPath,
                               String clientPrivateKeyPath,
                               String routerName, String ipPrefix, String nexthop) {
        if (StringUtils.isBlank(routerName) || StringUtils.isBlank(ipPrefix) || StringUtils.isBlank(nexthop)) {
            throw new CloudRuntimeException("Static route arguments are incomplete");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lrTable = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            GenericTableSchema srTable = schema.table(LOGICAL_ROUTER_STATIC_ROUTE_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lrNameCol = lrTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> srPrefixCol = srTable.column("ip_prefix", String.class);
            ColumnSchema<GenericTableSchema, String> srNexthopCol = srTable.column("nexthop", String.class);

            Operation<GenericTableSchema> selExisting = OVSDB_OPS.select(srTable).column(srPrefixCol)
                    .where(srPrefixCol.opEqual(ipPrefix)).and(srNexthopCol.opEqual(nexthop)).build();
            List<OperationResult> existing = client.transact(schema, Collections.<Operation>singletonList(selExisting))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (existing != null && !existing.isEmpty()
                    && existing.get(0).getRows() != null && !existing.get(0).getRows().isEmpty()) {
                logger.debug("Static_Route {}→{} already exists - skipping", ipPrefix, nexthop);
                return null;
            }

            Insert<GenericTableSchema> insertSr = OVSDB_OPS.insert(srTable)
                    .withId("newsr")
                    .value(srPrefixCol, ipPrefix)
                    .value(srNexthopCol, nexthop);
            ColumnSchema<GenericTableSchema, Set<UUID>> lrRoutesCol = lrTable.multiValuedColumn("static_routes", UUID.class);
            List<Operation> ops = new ArrayList<>();
            ops.add(insertSr);
            ops.add(OVSDB_OPS.mutate(lrTable)
                    .addMutation(lrRoutesCol, Mutator.INSERT, Collections.singleton(new UUID("newsr")))
                    .where(lrNameCol.opEqual(routerName)).build());
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("add Static_Route %s→%s on %s", ipPrefix, nexthop, routerName));
            logger.info("Added OVN Static_Route [{} → {}] on Logical_Router [{}] at {}", ipPrefix, nexthop, routerName, nbConnection);
            return null;
        });
    }

    /**
     * Idempotently creates or updates a Load_Balancer row keyed by name. {@code vips} is a map of
     * {@code "<vip_ip>:<vip_port>"} → {@code "<backend_ip>:<backend_port>[,...]"}. {@code protocol}
     * must be {@code tcp}, {@code udp} or {@code sctp}. Existing row is reset to the supplied
     * vips/protocol/options/external_ids - the row name is the stable identifier.
     *
     * <p>The OVN NB schema for Load_Balancer.vips is a {@code map<string,string>}; OVN
     * north interprets each entry as one DNAT mapping and may rewrite both IP and port (which is
     * exactly what we need for CloudStack PortForwarding rules where source and destination ports
     * can differ).</p>
     */
    public void createOrReplaceLoadBalancer(String nbConnection, String caCertPath, String clientCertPath,
                                            String clientPrivateKeyPath,
                                            String name, String protocol, Map<String, String> vips,
                                            Map<String, String> externalIds, Map<String, String> options) {
        // Backward-compat wrapper for callers (PortForwarding) that don't need selection_fields
        // or ip_port_mappings (which are LB-rule-specific concerns: hashing fields and HC source
        // attribution respectively).
        createOrReplaceLoadBalancer(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath,
                name, protocol, vips, externalIds, options, null, null);
    }

    public void createOrReplaceLoadBalancer(String nbConnection, String caCertPath, String clientCertPath,
                                            String clientPrivateKeyPath,
                                            String name, String protocol, Map<String, String> vips,
                                            Map<String, String> externalIds, Map<String, String> options,
                                            Set<String> selectionFields, Map<String, String> ipPortMappings) {
        if (StringUtils.isBlank(name)) {
            throw new CloudRuntimeException("Load_Balancer name is blank");
        }
        if (vips == null || vips.isEmpty()) {
            throw new CloudRuntimeException("Load_Balancer vips must be non-empty");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lbTable = schema.table(LOAD_BALANCER_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> nameCol = lbTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, UUID> uuidCol = lbTable.column("_uuid", UUID.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> vipsCol = lbTable.column("vips", Map.class);
            ColumnSchema<GenericTableSchema, Set<String>> protoCol = lbTable.multiValuedColumn("protocol", String.class);
            ColumnSchema<GenericTableSchema, Set<String>> selFieldsCol = lbTable.multiValuedColumn("selection_fields", String.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> ippmCol = lbTable.column("ip_port_mappings", Map.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> optsCol = lbTable.column("options", Map.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> extIdsCol = lbTable.column("external_ids", Map.class);

            // Look up existing row by name.
            Operation<GenericTableSchema> sel = OVSDB_OPS.select(lbTable).column(uuidCol)
                    .where(nameCol.opEqual(name)).build();
            List<OperationResult> selResult = client.transact(schema, Collections.<Operation>singletonList(sel))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            UUID existing = null;
            if (selResult != null && !selResult.isEmpty() && selResult.get(0).getRows() != null
                    && !selResult.get(0).getRows().isEmpty()) {
                existing = selResult.get(0).getRows().get(0).getColumn(uuidCol).getData();
            }

            List<Operation> ops = new ArrayList<>();
            if (existing == null) {
                Insert<GenericTableSchema> insert = OVSDB_OPS.insert(lbTable)
                        .value(nameCol, name)
                        .value(vipsCol, new HashMap<>(vips));
                if (StringUtils.isNotBlank(protocol)) {
                    insert = insert.value(protoCol, Collections.singleton(protocol));
                }
                if (options != null && !options.isEmpty()) {
                    insert = insert.value(optsCol, new HashMap<>(options));
                }
                if (externalIds != null && !externalIds.isEmpty()) {
                    insert = insert.value(extIdsCol, new HashMap<>(externalIds));
                }
                if (selectionFields != null && !selectionFields.isEmpty()) {
                    insert = insert.value(selFieldsCol, new java.util.HashSet<>(selectionFields));
                }
                if (ipPortMappings != null && !ipPortMappings.isEmpty()) {
                    insert = insert.value(ippmCol, new HashMap<>(ipPortMappings));
                }
                ops.add(insert);
            } else {
                // Replace contents in-place. Mutate explicit columns even if empty so stale entries
                // from a prior revision do not leak through.
                ops.add(OVSDB_OPS.update(lbTable)
                        .set(vipsCol, new HashMap<>(vips))
                        .set(protoCol, StringUtils.isNotBlank(protocol)
                                ? Collections.singleton(protocol)
                                : Collections.<String>emptySet())
                        .set(selFieldsCol, selectionFields != null
                                ? new java.util.HashSet<>(selectionFields)
                                : Collections.<String>emptySet())
                        .set(ippmCol, ipPortMappings != null ? new HashMap<>(ipPortMappings) : new HashMap<>())
                        .set(optsCol, options != null ? new HashMap<>(options) : new HashMap<>())
                        .set(extIdsCol, externalIds != null ? new HashMap<>(externalIds) : new HashMap<>())
                        .where(uuidCol.opEqual(existing)).build());
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("createOrReplace Load_Balancer %s", name));
            logger.info("Wrote Load_Balancer [{}] vips={} protocol={} options={} selection_fields={} at {}",
                    name, vips, protocol, options, selectionFields, nbConnection);
            return null;
        });
    }

    /**
     * Sets {@code Load_Balancer.health_check} to a single fresh Load_Balancer_Health_Check row
     * with the given vip+options. Existing HC rows (referenced or orphaned with our external_ids
     * tag) are deleted before insert so we never accumulate dead HC rows. OVN's health check is
     * L4 TCP-only - the {@code options} map carries {@code interval}, {@code timeout},
     * {@code success_count}, {@code failure_count}.
     *
     * <p>{@code ipPortMappings} populates {@code Load_Balancer.ip_port_mappings} so the SB
     * Service_Monitor knows from which logical port to source HC probes to each backend
     * (Format: {@code "<backend_ip>"} → {@code "<lsp_name>:<source_ip>"}).</p>
     */
    public void setLoadBalancerHealthCheck(String nbConnection, String caCertPath, String clientCertPath,
                                            String clientPrivateKeyPath,
                                            String lbName, String hcVip,
                                            Map<String, String> hcOptions,
                                            Map<String, String> ipPortMappings,
                                            Map<String, String> hcExternalIds) {
        if (StringUtils.isBlank(lbName) || StringUtils.isBlank(hcVip)) {
            throw new CloudRuntimeException("setLoadBalancerHealthCheck: arguments are incomplete");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lbTable = schema.table(LOAD_BALANCER_TABLE, GenericTableSchema.class);
            GenericTableSchema hcTable = schema.table(LOAD_BALANCER_HEALTH_CHECK_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lbNameCol = lbTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, UUID> lbUuidCol = lbTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lbHcCol = lbTable.multiValuedColumn("health_check", UUID.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> lbIppmCol = lbTable.column("ip_port_mappings", Map.class);
            ColumnSchema<GenericTableSchema, UUID> hcUuidCol = hcTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, String> hcVipCol = hcTable.column("vip", String.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> hcOptsCol = hcTable.column("options", Map.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> hcExtCol = hcTable.column("external_ids", Map.class);

            // Lookup LB; pull current health_check refs so we can delete them.
            Operation<GenericTableSchema> selLb = OVSDB_OPS.select(lbTable).column(lbUuidCol).column(lbHcCol)
                    .where(lbNameCol.opEqual(lbName)).build();
            List<OperationResult> lbResult = client.transact(schema, Collections.<Operation>singletonList(selLb))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (lbResult == null || lbResult.isEmpty() || lbResult.get(0).getRows() == null
                    || lbResult.get(0).getRows().isEmpty()) {
                throw new CloudRuntimeException("Load_Balancer " + lbName + " not found while setting health check");
            }
            UUID lbUuid = lbResult.get(0).getRows().get(0).getColumn(lbUuidCol).getData();
            @SuppressWarnings("unchecked")
            Set<UUID> oldHc = (Set<UUID>) lbResult.get(0).getRows().get(0).getColumn(lbHcCol).getData();

            String namedUuid = "newhc";
            Insert<GenericTableSchema> insertHc = OVSDB_OPS.insert(hcTable)
                    .withId(namedUuid)
                    .value(hcVipCol, hcVip)
                    .value(hcOptsCol, hcOptions != null ? new HashMap<>(hcOptions) : new HashMap<>());
            if (hcExternalIds != null && !hcExternalIds.isEmpty()) {
                insertHc = insertHc.value(hcExtCol, new HashMap<>(hcExternalIds));
            }

            List<Operation> ops = new ArrayList<>();
            ops.add(insertHc);
            // Replace the LB.health_check set and refresh ip_port_mappings in the same txn.
            ops.add(OVSDB_OPS.update(lbTable)
                    .set(lbHcCol, Collections.singleton(new UUID(namedUuid)))
                    .set(lbIppmCol, ipPortMappings != null ? new HashMap<>(ipPortMappings) : new HashMap<>())
                    .where(lbUuidCol.opEqual(lbUuid)).build());
            // Delete the old HC rows now that no LB references them (strong ref).
            if (oldHc != null) {
                for (UUID stale : oldHc) {
                    ops.add(OVSDB_OPS.delete(hcTable).where(hcUuidCol.opEqual(stale)).build());
                }
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("set health check on Load_Balancer %s", lbName));
            logger.info("Set Load_Balancer_Health_Check on [{}] vip={} options={} ipPortMappings={} at {}",
                    lbName, hcVip, hcOptions, ipPortMappings, nbConnection);
            return null;
        });
    }

    /**
     * Removes any {@code Load_Balancer_Health_Check} row attached to {@code lbName} and clears
     * the LB's {@code ip_port_mappings}. Used when a CloudStack LB rule's HealthCheckPolicy is
     * revoked or absent. Idempotent: a no-op when the LB has no HC.
     */
    public void clearLoadBalancerHealthCheck(String nbConnection, String caCertPath, String clientCertPath,
                                              String clientPrivateKeyPath,
                                              String lbName) {
        if (StringUtils.isBlank(lbName)) {
            throw new CloudRuntimeException("clearLoadBalancerHealthCheck: name is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lbTable = schema.table(LOAD_BALANCER_TABLE, GenericTableSchema.class);
            GenericTableSchema hcTable = schema.table(LOAD_BALANCER_HEALTH_CHECK_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lbNameCol = lbTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, UUID> lbUuidCol = lbTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lbHcCol = lbTable.multiValuedColumn("health_check", UUID.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> lbIppmCol = lbTable.column("ip_port_mappings", Map.class);
            ColumnSchema<GenericTableSchema, UUID> hcUuidCol = hcTable.column("_uuid", UUID.class);

            Operation<GenericTableSchema> selLb = OVSDB_OPS.select(lbTable).column(lbUuidCol).column(lbHcCol)
                    .where(lbNameCol.opEqual(lbName)).build();
            List<OperationResult> lbResult = client.transact(schema, Collections.<Operation>singletonList(selLb))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (lbResult == null || lbResult.isEmpty() || lbResult.get(0).getRows() == null
                    || lbResult.get(0).getRows().isEmpty()) {
                return null;
            }
            UUID lbUuid = lbResult.get(0).getRows().get(0).getColumn(lbUuidCol).getData();
            @SuppressWarnings("unchecked")
            Set<UUID> oldHc = (Set<UUID>) lbResult.get(0).getRows().get(0).getColumn(lbHcCol).getData();
            if (oldHc == null || oldHc.isEmpty()) {
                return null;
            }
            List<Operation> ops = new ArrayList<>();
            ops.add(OVSDB_OPS.update(lbTable)
                    .set(lbHcCol, Collections.<UUID>emptySet())
                    .set(lbIppmCol, new HashMap<>())
                    .where(lbUuidCol.opEqual(lbUuid)).build());
            for (UUID stale : oldHc) {
                ops.add(OVSDB_OPS.delete(hcTable).where(hcUuidCol.opEqual(stale)).build());
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("clear health check on Load_Balancer %s", lbName));
            logger.info("Cleared {} Load_Balancer_Health_Check row(s) from [{}] at {}",
                    oldHc.size(), lbName, nbConnection);
            return null;
        });
    }

    /**
     * Idempotently links a Load_Balancer (by name) into a Logical_Router's {@code load_balancer}
     * set. Used to make ovn-northd evaluate the LB DNAT pipeline on traffic arriving at this LR.
     */
    public void attachLoadBalancerToRouter(String nbConnection, String caCertPath, String clientCertPath,
                                            String clientPrivateKeyPath,
                                            String routerName, String lbName) {
        if (StringUtils.isBlank(routerName) || StringUtils.isBlank(lbName)) {
            throw new CloudRuntimeException("Logical_Router/Load_Balancer name is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lrTable = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            GenericTableSchema lbTable = schema.table(LOAD_BALANCER_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lrNameCol = lrTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> lbNameCol = lbTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, UUID> lbUuidCol = lbTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lrLbCol = lrTable.multiValuedColumn("load_balancer", UUID.class);

            UUID lbUuid = lookupUuidByName(client, schema, lbTable, lbNameCol, lbUuidCol, lbName);
            if (lbUuid == null) {
                throw new CloudRuntimeException("Load_Balancer " + lbName + " not found while attaching to LR " + routerName);
            }
            Operation<GenericTableSchema> mutate = OVSDB_OPS.mutate(lrTable)
                    .addMutation(lrLbCol, Mutator.INSERT, Collections.singleton(lbUuid))
                    .where(lrNameCol.opEqual(routerName)).build();
            List<OperationResult> results = client.transact(schema, Collections.singletonList(mutate))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("attach LB %s to LR %s", lbName, routerName));
            logger.debug("Attached Load_Balancer [{}] to Logical_Router [{}] at {}", lbName, routerName, nbConnection);
            return null;
        });
    }

    /**
     * Idempotently links a Load_Balancer (by name) into a Logical_Switch's {@code load_balancer}
     * set. Required for return-traffic visibility (RHBZ#2043543) when a VM on this switch is the
     * backend of a port-forwarding rule attached to the upstream router.
     */
    public void attachLoadBalancerToSwitch(String nbConnection, String caCertPath, String clientCertPath,
                                            String clientPrivateKeyPath,
                                            String switchName, String lbName) {
        if (StringUtils.isBlank(switchName) || StringUtils.isBlank(lbName)) {
            throw new CloudRuntimeException("Logical_Switch/Load_Balancer name is blank");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lsTable = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            GenericTableSchema lbTable = schema.table(LOAD_BALANCER_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lsNameCol = lsTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, String> lbNameCol = lbTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, UUID> lbUuidCol = lbTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lsLbCol = lsTable.multiValuedColumn("load_balancer", UUID.class);

            UUID lbUuid = lookupUuidByName(client, schema, lbTable, lbNameCol, lbUuidCol, lbName);
            if (lbUuid == null) {
                throw new CloudRuntimeException("Load_Balancer " + lbName + " not found while attaching to LS " + switchName);
            }
            Operation<GenericTableSchema> mutate = OVSDB_OPS.mutate(lsTable)
                    .addMutation(lsLbCol, Mutator.INSERT, Collections.singleton(lbUuid))
                    .where(lsNameCol.opEqual(switchName)).build();
            List<OperationResult> results = client.transact(schema, Collections.singletonList(mutate))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("attach LB %s to LS %s", lbName, switchName));
            logger.debug("Attached Load_Balancer [{}] to Logical_Switch [{}] at {}", lbName, switchName, nbConnection);
            return null;
        });
    }

    /**
     * Removes every Load_Balancer row whose {@code external_ids} contains {@code key=value}. First
     * walks every Logical_Router and Logical_Switch, mutates their {@code load_balancer} sets to
     * detach the matching LBs, then deletes the LB rows themselves. Detach is required because
     * {@code load_balancer} is a strong reference set in the OVN NB schema.
     */
    public int removeLoadBalancersByExternalId(String nbConnection, String caCertPath, String clientCertPath,
                                                 String clientPrivateKeyPath,
                                                 String externalIdKey, String externalIdValue) {
        if (StringUtils.isBlank(externalIdKey) || StringUtils.isBlank(externalIdValue)) {
            throw new CloudRuntimeException("removeLoadBalancersByExternalId arguments are incomplete");
        }
        return runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lbTable = schema.table(LOAD_BALANCER_TABLE, GenericTableSchema.class);
            GenericTableSchema lrTable = schema.table(LOGICAL_ROUTER_TABLE, GenericTableSchema.class);
            GenericTableSchema lsTable = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, UUID> lbUuidCol = lbTable.column("_uuid", UUID.class);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ColumnSchema<GenericTableSchema, Map> lbExtCol = lbTable.column("external_ids", Map.class);
            ColumnSchema<GenericTableSchema, UUID> lrUuidCol = lrTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lrLbCol = lrTable.multiValuedColumn("load_balancer", UUID.class);
            ColumnSchema<GenericTableSchema, UUID> lsUuidCol = lsTable.column("_uuid", UUID.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lsLbCol = lsTable.multiValuedColumn("load_balancer", UUID.class);

            // Step 1: collect LB UUIDs whose external_ids match.
            Operation<GenericTableSchema> selLb = OVSDB_OPS.select(lbTable).column(lbUuidCol).column(lbExtCol);
            List<OperationResult> lbResult = client.transact(schema, Collections.<Operation>singletonList(selLb))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            Set<UUID> matchedLbs = new java.util.HashSet<>();
            if (lbResult != null && !lbResult.isEmpty() && lbResult.get(0).getRows() != null) {
                for (Row<GenericTableSchema> row : lbResult.get(0).getRows()) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> ext = (Map<String, String>) row.getColumn(lbExtCol).getData();
                    if (ext != null && externalIdValue.equals(ext.get(externalIdKey))) {
                        matchedLbs.add(row.getColumn(lbUuidCol).getData());
                    }
                }
            }
            if (matchedLbs.isEmpty()) {
                return 0;
            }

            // Step 2: walk LRs/LSes and detach. We pull the full set then mutate per row that
            // actually contains one of our UUIDs - keeps the transaction small.
            List<Operation> ops = new ArrayList<>();
            collectDetachOps(client, schema, lrTable, lrUuidCol, lrLbCol, matchedLbs, ops);
            collectDetachOps(client, schema, lsTable, lsUuidCol, lsLbCol, matchedLbs, ops);

            // Step 3: delete the LB rows themselves.
            for (UUID u : matchedLbs) {
                ops.add(OVSDB_OPS.delete(lbTable).where(lbUuidCol.opEqual(u)).build());
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("remove LBs by %s=%s", externalIdKey, externalIdValue));
            logger.info("Removed {} Load_Balancer row(s) tagged [{}={}] at {}",
                    matchedLbs.size(), externalIdKey, externalIdValue, nbConnection);
            return matchedLbs.size();
        });
    }

    private void collectDetachOps(OvsdbClient client, DatabaseSchema schema,
                                   GenericTableSchema parentTable,
                                   ColumnSchema<GenericTableSchema, UUID> parentUuidCol,
                                   ColumnSchema<GenericTableSchema, Set<UUID>> lbRefCol,
                                   Set<UUID> targetLbUuids,
                                   List<Operation> ops) throws Exception {
        Operation<GenericTableSchema> sel = OVSDB_OPS.select(parentTable).column(parentUuidCol).column(lbRefCol);
        List<OperationResult> result = client.transact(schema, Collections.<Operation>singletonList(sel))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        if (result == null || result.isEmpty() || result.get(0).getRows() == null) {
            return;
        }
        for (Row<GenericTableSchema> row : result.get(0).getRows()) {
            @SuppressWarnings("unchecked")
            Set<UUID> refs = (Set<UUID>) row.getColumn(lbRefCol).getData();
            if (refs == null || refs.isEmpty()) continue;
            Set<UUID> overlap = new java.util.HashSet<>(refs);
            overlap.retainAll(targetLbUuids);
            if (overlap.isEmpty()) continue;
            UUID parentUuid = row.getColumn(parentUuidCol).getData();
            ops.add(OVSDB_OPS.mutate(parentTable)
                    .addMutation(lbRefCol, Mutator.DELETE, overlap)
                    .where(parentUuidCol.opEqual(parentUuid)).build());
        }
    }

    private UUID lookupUuidByName(OvsdbClient client, DatabaseSchema schema,
                                   GenericTableSchema table,
                                   ColumnSchema<GenericTableSchema, String> nameCol,
                                   ColumnSchema<GenericTableSchema, UUID> uuidCol,
                                   String name) throws Exception {
        Operation<GenericTableSchema> sel = OVSDB_OPS.select(table).column(uuidCol)
                .where(nameCol.opEqual(name)).build();
        List<OperationResult> result = client.transact(schema, Collections.<Operation>singletonList(sel))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        if (result == null || result.isEmpty() || result.get(0).getRows() == null
                || result.get(0).getRows().isEmpty()) {
            return null;
        }
        return result.get(0).getRows().get(0).getColumn(uuidCol).getData();
    }

    /**
     * Idempotently installs an ACL row on the named Logical_Switch. The caller is expected to
     * tag the ACL via {@code external_ids} so subsequent revocation can target the row by tag
     * (e.g. {@code cloudstack_fw_rule_id=<rule_id>}). If a row with the same tag combination
     * already exists on this switch it is replaced - this keeps applyFWRules idempotent across
     * retries without leaking stale rows.
     *
     * <p>Logical_Switch.acls is a weak-ref set, so deleting the ACL row alone is enough to
     * break the link, but we still mutate {@code acls} explicitly to keep the LS row tidy.</p>
     */
    public void addAclOnLs(String nbConnection, String caCertPath, String clientCertPath,
                           String clientPrivateKeyPath,
                           String logicalSwitchName, String name, String direction, long priority,
                           String match, String action, Map<String, String> externalIds) {
        if (StringUtils.isBlank(logicalSwitchName) || StringUtils.isBlank(direction)
                || StringUtils.isBlank(match) || StringUtils.isBlank(action)) {
            throw new CloudRuntimeException("ACL arguments are incomplete");
        }
        if (externalIds == null || externalIds.isEmpty()) {
            throw new CloudRuntimeException("ACL external_ids must be set so the row can be replaced/removed by tag");
        }
        runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lsTable = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            GenericTableSchema aclTable = schema.table(ACL_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lsNameCol = lsTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lsAclsCol = lsTable.multiValuedColumn("acls", UUID.class);

            ColumnSchema<GenericTableSchema, String> aclDirCol = aclTable.column("direction", String.class);
            ColumnSchema<GenericTableSchema, Long> aclPrioCol = aclTable.column("priority", Long.class);
            ColumnSchema<GenericTableSchema, String> aclMatchCol = aclTable.column("match", String.class);
            ColumnSchema<GenericTableSchema, String> aclActionCol = aclTable.column("action", String.class);
            @SuppressWarnings("rawtypes")
            ColumnSchema<GenericTableSchema, Map> aclExtCol = aclTable.column("external_ids", Map.class);

            // First, remove any existing ACL on this LS that already carries the same external_ids
            // tag. We do this in the same transaction to keep the operation atomic.
            List<UUID> staleAclUuids = findAclUuidsByExternalIds(client, schema, aclTable, externalIds);
            List<Operation> ops = new ArrayList<>();
            ColumnSchema<GenericTableSchema, UUID> aclUuidCol = aclTable.column("_uuid", UUID.class);
            for (UUID stale : staleAclUuids) {
                ops.add(OVSDB_OPS.delete(aclTable).where(aclUuidCol.opEqual(stale)).build());
                ops.add(OVSDB_OPS.mutate(lsTable)
                        .addMutation(lsAclsCol, Mutator.DELETE, Collections.singleton(stale))
                        .where(lsNameCol.opEqual(logicalSwitchName)).build());
            }

            String namedUuid = "newacl";
            Insert<GenericTableSchema> insertAcl = OVSDB_OPS.insert(aclTable)
                    .withId(namedUuid)
                    .value(aclDirCol, direction)
                    .value(aclPrioCol, priority)
                    .value(aclMatchCol, match)
                    .value(aclActionCol, action)
                    .value(aclExtCol, new HashMap<>(externalIds));
            if (StringUtils.isNotBlank(name)) {
                ColumnSchema<GenericTableSchema, String> aclNameCol = aclTable.column("name", String.class);
                insertAcl = insertAcl.value(aclNameCol, name);
            }
            ops.add(insertAcl);
            ops.add(OVSDB_OPS.mutate(lsTable)
                    .addMutation(lsAclsCol, Mutator.INSERT, Collections.singleton(new UUID(namedUuid)))
                    .where(lsNameCol.opEqual(logicalSwitchName)).build());

            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("install ACL on Logical_Switch %s (priority=%d, action=%s)",
                    logicalSwitchName, priority, action));
            logger.info("Installed OVN ACL on Logical_Switch [{}] dir=[{}] prio=[{}] action=[{}] match=[{}] tags=[{}]",
                    logicalSwitchName, direction, priority, action, match, externalIds);
            return null;
        });
    }

    /**
     * Removes every ACL row whose {@code external_ids} contains the supplied (key, value) pair
     * and detaches it from the named Logical_Switch. Used to revoke individual firewall rules
     * (by {@code cloudstack_fw_rule_id}) or to wipe per-IP scopes (by {@code cloudstack_fw_ip}).
     */
    public int removeAclsOnLsByExternalId(String nbConnection, String caCertPath, String clientCertPath,
                                           String clientPrivateKeyPath,
                                           String logicalSwitchName, String externalIdKey, String externalIdValue) {
        if (StringUtils.isBlank(logicalSwitchName) || StringUtils.isBlank(externalIdKey) || StringUtils.isBlank(externalIdValue)) {
            throw new CloudRuntimeException("ACL removal arguments are incomplete");
        }
        return runOn(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, client -> {
            DatabaseSchema schema = client.getSchema(NORTHBOUND_DB).get(timeoutMs, TimeUnit.MILLISECONDS);
            GenericTableSchema lsTable = schema.table(LOGICAL_SWITCH_TABLE, GenericTableSchema.class);
            GenericTableSchema aclTable = schema.table(ACL_TABLE, GenericTableSchema.class);
            ColumnSchema<GenericTableSchema, String> lsNameCol = lsTable.column("name", String.class);
            ColumnSchema<GenericTableSchema, Set<UUID>> lsAclsCol = lsTable.multiValuedColumn("acls", UUID.class);
            ColumnSchema<GenericTableSchema, UUID> aclUuidCol = aclTable.column("_uuid", UUID.class);

            Map<String, String> filter = new HashMap<>();
            filter.put(externalIdKey, externalIdValue);
            List<UUID> uuids = findAclUuidsByExternalIds(client, schema, aclTable, filter);
            if (uuids.isEmpty()) {
                return 0;
            }
            List<Operation> ops = new ArrayList<>();
            for (UUID u : uuids) {
                ops.add(OVSDB_OPS.delete(aclTable).where(aclUuidCol.opEqual(u)).build());
                ops.add(OVSDB_OPS.mutate(lsTable)
                        .addMutation(lsAclsCol, Mutator.DELETE, Collections.singleton(u))
                        .where(lsNameCol.opEqual(logicalSwitchName)).build());
            }
            List<OperationResult> results = client.transact(schema, ops).get(timeoutMs, TimeUnit.MILLISECONDS);
            assertNoError(results, String.format("remove ACLs on %s by %s=%s", logicalSwitchName, externalIdKey, externalIdValue));
            logger.info("Removed {} OVN ACL row(s) on Logical_Switch [{}] tagged [{}={}]",
                    uuids.size(), logicalSwitchName, externalIdKey, externalIdValue);
            return uuids.size();
        });
    }

    /**
     * Returns the UUIDs of every ACL row whose {@code external_ids} map contains every entry
     * present in {@code wantedExternalIds}. We pull the column server-side and filter in the
     * client because OVSDB select with where-clause cannot match into a map column.
     */
    @SuppressWarnings("unchecked")
    private List<UUID> findAclUuidsByExternalIds(OvsdbClient client, DatabaseSchema schema,
                                                  GenericTableSchema aclTable,
                                                  Map<String, String> wantedExternalIds) throws Exception {
        ColumnSchema<GenericTableSchema, UUID> uuidCol = aclTable.column("_uuid", UUID.class);
        @SuppressWarnings("rawtypes")
        ColumnSchema<GenericTableSchema, Map> extIdsCol = aclTable.column("external_ids", Map.class);
        Operation<GenericTableSchema> selectAll = OVSDB_OPS.select(aclTable).column(uuidCol).column(extIdsCol);
        List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(selectAll))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        List<UUID> matches = new ArrayList<>();
        if (results == null || results.isEmpty() || results.get(0).getRows() == null) {
            return matches;
        }
        for (Row<GenericTableSchema> row : results.get(0).getRows()) {
            Map<String, String> ext = (Map<String, String>) row.getColumn(extIdsCol).getData();
            if (ext == null) continue;
            boolean ok = true;
            for (Map.Entry<String, String> e : wantedExternalIds.entrySet()) {
                if (!e.getValue().equals(ext.get(e.getKey()))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                matches.add(row.getColumn(uuidCol).getData());
            }
        }
        return matches;
    }

    private boolean natRuleExists(OvsdbClient client, DatabaseSchema schema, GenericTableSchema natTable,
                                   String natType, String externalIp, String logicalIp) throws Exception {
        ColumnSchema<GenericTableSchema, String> natTypeCol = natTable.column("type", String.class);
        ColumnSchema<GenericTableSchema, String> natExtCol = natTable.column("external_ip", String.class);
        ColumnSchema<GenericTableSchema, String> natLogCol = natTable.column("logical_ip", String.class);
        Operation<GenericTableSchema> select = OVSDB_OPS.select(natTable).column(natTypeCol)
                .where(natTypeCol.opEqual(natType))
                .and(natExtCol.opEqual(externalIp))
                .and(natLogCol.opEqual(logicalIp)).build();
        List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(select))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        if (results == null || results.isEmpty()) return false;
        OperationResult r = results.get(0);
        if (r.getError() != null) {
            throw new CloudRuntimeException("OVSDB select on NAT failed: " + r.getError());
        }
        List<Row<GenericTableSchema>> rows = r.getRows();
        return rows != null && !rows.isEmpty();
    }

    private boolean rowExistsByName(OvsdbClient client, DatabaseSchema schema,
                                     GenericTableSchema table, ColumnSchema<GenericTableSchema, String> nameCol,
                                     String name) throws Exception {
        Operation<GenericTableSchema> select = OVSDB_OPS.select(table).column(nameCol).where(nameCol.opEqual(name)).build();
        List<OperationResult> results = client.transact(schema, Collections.<Operation>singletonList(select))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        if (results == null || results.isEmpty()) return false;
        OperationResult r = results.get(0);
        if (r.getError() != null) {
            throw new CloudRuntimeException("OVSDB select failed: " + r.getError());
        }
        List<Row<GenericTableSchema>> rows = r.getRows();
        return rows != null && !rows.isEmpty();
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
        return runOnDb(nbConnection, caCertPath, clientCertPath, clientPrivateKeyPath, NORTHBOUND_DB, action);
    }

    private <T> T runOnDb(String nbConnection, String caCertPath, String clientCertPath, String clientPrivateKeyPath,
                          String expectedDb, NbAction<T> action) {
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
                throw new CloudRuntimeException(String.format("OVN %s at %s did not accept the connection", expectedDb, nbConnection));
            }
            return action.call(client);
        } catch (CloudRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CloudRuntimeException("OVN " + expectedDb + " operation against " + nbConnection + " failed: " + e.getMessage(), e);
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
