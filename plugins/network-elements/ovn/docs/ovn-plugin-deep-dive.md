---
marp: true
theme: default
paginate: true
size: 16:9
header: 'Apache CloudStack — OVN Plugin Deep Dive'
footer: '4.23.0-SNAPSHOT · ovn-vpc-peering branch'
style: |
  section { font-size: 22px; }
  h1 { color: #1f4e79; }
  h2 { color: #1f4e79; border-bottom: 2px solid #1f4e79; padding-bottom: 4px; }
  code { background: #f4f4f4; padding: 1px 5px; border-radius: 3px; }
  table { font-size: 18px; }
  pre { font-size: 16px; line-height: 1.3; }
  .small { font-size: 18px; }
  .tiny { font-size: 14px; }
---

<!-- _class: lead -->

# Apache CloudStack OVN Plugin
## Network virtualization without the virtual router

A technical deep dive into how OVN replaces the legacy VR data plane,
the network services it delivers, and the new **VPC Peering** subsystem.

---

## Why OVN

CloudStack's reference data plane is the **VR (virtual router)**: one tenant-side
VM per network/VPC carrying SNAT, DHCP, DNS, LB, port-forward, ACLs in user space.

| | Legacy VR | OVN |
|---|---|---|
| Data plane | Per-tenant VM (Linux) | Hypervisor OVS, programmed by `ovn-controller` |
| Failure domain | One VM | Distributed across all hosts |
| HA | Active/Standby pair | Built-in, distributed |
| Scale ceiling | VR CPU/NIC | OVS flow tables |
| New service | Patch & redeploy VR template | `ovsdb` schema row |
| L3 | iptables, conntrack | Logical Router + flow rules |

**Goal of the plugin:** wire CloudStack's networking model (Networks, VPCs, NICs,
Public IPs) directly into the **OVN Northbound DB** so the data plane is OVN-native.

---

## Plugin scope

```
plugins/network-elements/ovn/
├── api/      # CreateOvnProvider, ListOvnProviders, *MeshNetwork, ...
├── service/  # OvnElement (NetworkElement impl) + OvnNbClient
└── resources # XML beans, applicationContext

engine/schema/com/cloud/network/
├── element/OvnMeshNetworkVO.java
└── dao/OvnMeshNetworkDao{,Impl}.java
```

- One Spring-managed `NetworkElement` per zone-scoped OVN provider
- Direct **OVSDB JSON-RPC** to the zone's OVN-NB (no `ovn-nbctl` shellouts)
- Optional **OVN-IC** (interconnection) wiring for cross-zone topology

---

## Provider model

Each AZ has its own `ovn_providers` row pointing at:

| Column | Meaning |
|---|---|
| `nb_connection` | `ssl:host:6641` — zone OVN-NB |
| `sb_connection` | `ssl:host:6642` — zone OVN-SB (used to look up chassis) |
| `ic_nb_connection` | `ssl:host:6645` — global IC NB (optional, enables cross-zone) |
| `ca_cert_path`, `client_cert_path`, `client_private_key_path` | mTLS material |

`OvnProviderDao.findByZoneId(...)` resolves the connection set used
by every operation that touches data plane state in that zone.

---

## OVN terminology cheat-sheet

| Acronym | Full name | Role |
|---|---|---|
| **NB** | Northbound DB | High-level intent: *what* the network looks like (LS, LR, ACLs, NAT). Plugin writes here. |
| **SB** | Southbound DB | Compiled flows + chassis state. `ovn-northd` populates from NB; `ovn-controller` reads it. |
| **IC-NB / IC-SB** | Interconnection NB / SB | Global DBs shared by zones for OVN-IC; hold `Transit_Switch` rows and learned routes. |
| **LS** | Logical Switch | L2 broadcast domain. CS Network → one LS. |
| **LSP** | Logical Switch Port | A port on an LS. NIC, router-side, localnet, or peer-attachment. |
| **LR** | Logical Router | L3 router. CS VPC → one LR (`cs-vpc-<id>`). |
| **LRP** | Logical Router Port | A router interface; pairs with an LSP on an attached LS. Carries IP, MAC, peers. |
| **DGP** | Distributed Gateway Port | Special LRP that owns external IPs and runs centralised NAT/ARP on a `gateway_chassis`. |
| **NAT** | NAT row on an LR | `snat`, `dnat`, or `dnat_and_snat`. CS public IPs / port-forwards become rows here. |
| **TS** | Transit Switch | Logical Switch in IC-NB that interconnects LRs across zones. Cross-zone peering uses one. |
| **ACL** | ACL row on an LS or LR | Stateful filter (`from-lport` / `to-lport`). Per-tier and per-peering rules live here. |
| **`localnet`** | LSP type | Bridges an LS to a physical bridge (`ovn-bridge-mappings`) — public/external uplink. |

---

## What OVN delivers — services

`OvnElement.getCapabilities()` declares:

| Service | Capability | OVN object that implements it |
|---|---|---|
| **Connectivity** | StretchedL2 / RegionLevel | Logical Switch + Logical Router |
| **DHCP / DNS** | Server-side | LS port `dhcp_options` |
| **UserData** | ConfigDrive | hypervisor `cloud_init` ISO (zone-level) |
| **SourceNat** | redundant | LR `nat` + distributed gateway port |
| **StaticNat** | per-IP | LR `nat` |
| **PortForwarding** | TCP/UDP | LR `nat` (`dnat_and_snat`) |
| **NetworkACL** | per-tier | LS `acls` (stateful) |
| **Firewall** | egress + ingress | LR/LS `acls` (added in this branch) |

**Not** delivered by OVN: LB, VPN, IPv6 (VR retains these in mixed offerings).

---

## How CloudStack objects map to OVN

```
CS Network (isolated)        OVN Logical Switch  (cs-net-<id>)
CS VPC                       OVN Logical Router  (cs-vpc-<id>)
  └─ tier (Network)           └─ LRP + LSP pair  (lrp-cs-net-<id>, lsp-cs-vpc-<id>)
CS NIC                       OVN Logical_Switch_Port  (vm-<vmId>-nic-<nicId>)
CS Public IP                 OVN NAT row
CS Static Route              OVN Logical_Router_Static_Route
CS Egress/ACL rule           OVN ACL row on LS / LR
```

Naming is deterministic — `OvnElement` derives every object name from CloudStack
IDs, so reconciliation after restart-with-cleanup is idempotent.

---

## Lifecycle of a tier creation

```
implement(network)
  └─ ensureVpcLogicalRouter(vpc)         # creates cs-vpc-<id> if missing
  └─ ensureTierLogicalSwitch(network)    # creates cs-net-<id>
  └─ attachTierToRouter(network, vpc)    # LRP+LSP, sets DHCP/DNS options
  └─ ensureExternalSnat(vpc)             # LR distributed-gateway-port + SNAT
```

```
prepare(nic)
  └─ ovnNbClient.createLogicalSwitchPort(...)  # MAC + IP + dynamic_addresses

release(nic)  → ovnNbClient.deleteLogicalSwitchPort(...)
shutdown(network)/shutdownVpc → cascading cleanup
```

All idempotent. `restart-with-cleanup` re-runs `implement` and `prepare`
without orphaning state.

---

## Distributed gateway & external connectivity

For each VPC's LR:

- **gateway_chassis** — picks one (or HA priority list) from chassis where
  `other_config:ovn_bridge_mappings` contains the `physical` net
- **distributed_gateway_port** — egress LRP attached to a `provider` LS that
  bridges to the public physical network via `localnet`
- **NAT rules** — `snat` for default outbound, `dnat_and_snat` for floating
  / static NAT, `dnat_and_snat` with `external_port` for port forwarding

ARP ownership of public IPs is the gateway-chassis. **Failure of the gateway
host is recovered by re-pinning** to the next priority — `ovn-northd`
re-publishes the flow distribution.

---

## Why a peering subsystem

VPC tiers can talk to each other inside a VPC (via the VPC's LR).
**Across VPCs**, traffic has to leave the VPC and re-enter via SNAT — which
breaks intra-tenant private addressing and per-VPC ACLs.

CloudStack's existing answer is **Private Gateway** + static routes — but it
requires admin to set `broadcastUri`, allocates a real network, and can't model
N×N propagation in a mesh.

**OVN VPC Peering** = a dedicated peering object that sets up an internal
peering fabric in OVN, **bypassing public IPs and SNAT**, with stateful ACLs
applied at the peering boundary.

---

## Peering topology — same zone

A peering "group" identified by `mesh_uuid` provisions:

```
                  ┌─────────────── peering LS ───────────────┐
                  │    cs-mesh-<meshUuid>  (zone-local)     │
                  └──┬───────────────┬───────────────┬───────┘
                     │ .1/30         │ .5/30         │ .9/30
              ┌──────┴────┐   ┌──────┴────┐   ┌──────┴────┐
              │ cs-vpc-A  │   │ cs-vpc-B  │   │ cs-vpc-C  │   logical routers
              └───────────┘   └───────────┘   └───────────┘
```

- /30 link-local pool **`169.254.100.0/24`**
- Per-router static routes pointing at every other peer's CIDR via its LL IP
- `Logical_Router_Policy` (priority 1000, `reroute`) **bypasses SNAT** for
  destinations inside any peered CIDR

---

## Peering topology — cross zone

Same-zone peering LSes don't traverse zone boundaries. For multi-AZ peering
the plugin uses **OVN Interconnection (OVN-IC)**:

```
        Zone Z1 NB                       Zone Z2 NB
   ┌──────────────────┐             ┌──────────────────┐
   │  cs-vpc-A  ──┐   │             │   ┌── cs-vpc-D   │
   │  cs-vpc-B ──┤    │             │   │              │
   └─────────────┼────┘             └───┼──────────────┘
                 │   ┌──────────────────┴──┐
                 └──>│  Transit_Switch     │<─┐
                     │  ts-mesh-<group>    │  │  IC_NB (global)
                     └─────────────────────┘
```

- Pool **`169.254.200.0/24`** (separated from same-zone)
- `ovn-ic` daemon learns LRPs and **route-advertises** peer CIDRs zone-to-zone
- Per-router NAT bypass policies + ACLs are still local to each zone

---

## Data plane elements per peering

Per mesh_uuid, per member VPC, the plugin creates:

| Element | Purpose | External_id tag |
|---|---|---|
| LRP on `cs-vpc-<id>` | Router port into peering LS / TS | `cloudstack_mesh_network=<meshUuid>` |
| LSP on peering LS / TS | Counterpart of the LRP | `cloudstack_mesh_network=<meshUuid>` |
| Static route per peer CIDR | Forward traffic to peer's LL IP | `cloudstack_mesh_network=<meshUuid>` |
| LR Policy `reroute` priority 1000 | Skip SNAT for peered CIDRs | `cloudstack_mesh_network_target=<peerVpcId>` |
| ACL row on peering LS | Apply VPC's `aclid` to its peering traffic | `cloudstack_mesh_acl_vpc_<vpcId>=true` |

Bulk cleanup uses `removeStaticRoutesByExternalId` /
`removeLogicalRouterPoliciesByExternalId` — DB rows aren't tracked per object.

---

## Persistence model

```sql
CREATE TABLE ovn_mesh_networks (
    id, uuid, mesh_uuid,
    vpc_id, zone_id, account_id, domain_id,
    link_local_ip,      -- pool prefix encodes same vs cross zone
    acl_id,             -- per-member ACL applied at peering boundary
    state,              -- Active | Disabled | Removed
    created, removed
);
```

- One row **per VPC** in a peering mesh; mesh_uuid is the natural key
- `link_local_ip` is the source of truth for cross-zone vs same-zone
  detection — survives bulk-delete iterations that shrink the Active set
- `acl_id` nullable → null means "Default Allow All"

---

## API surface

| Command | Role | Body |
|---|---|---|
| `createMeshNetwork` | Add a VPC to a group | `{name, vpcid, peervpcid, [aclid]}` |
| `listMeshNetworks` | Returns aggregated **groups** with `members[]` | `{vpcid?, meshuuid?}` |
| `updateMeshNetwork` | Change a member's ACL | `{id, aclid?}` |
| `deleteMeshNetwork` | Remove a member, or whole group | `{id}` (peering-uuid or group-uuid) |
| `enableMeshNetwork` | Re-provision OVN data plane | `{id}` |
| `disableMeshNetwork` | Tear down data plane, keep DB | `{id}` |

All authorized for **User** role (no admin gate). The mesh adds itself: a
second `createMeshNetwork` with `peervpcid` already in a group joins that
existing `mesh_uuid` instead of starting a new one.

---

## State machine

```
                ┌──────────┐  enableMeshNetwork   ┌──────────┐
                │ Disabled │ ──────────────────> │  Active  │
                └────┬─────┘ <───────────────────└────┬─────┘
                     │       disableMeshNetwork        │
                     │                                │
                     │  deleteMeshNetwork              │
                     ▼                                ▼
                ┌────────────────────────────────────────┐
                │              Removed                    │
                └────────────────────────────────────────┘
```

- **Active** — full OVN fabric in place
- **Disabled** — DB row + LL IP reserved, OVN data plane torn down
  (idempotent re-`enable` rebuilds via `provisionMeshNetwork`)
- **Removed** — terminal; LL IP slot can be reused

---

## Use case 1 — multi-tier app spanning two VPCs

```
   VPC-app  (10.0.0.0/16)            VPC-data  (10.1.0.0/16)
     ├─ web       10.0.10.0/24        ├─ db          10.1.10.0/24
     ├─ api       10.0.20.0/24        ├─ cache       10.1.20.0/24
     └─ <peer>───────────────────────────<peer>
```

- Decouples app and data lifecycles — separate VPC ownership, separate ACLs
- DB tier never gets a public IP; the only ingress path is the peering
- Per-VPC ACL on the peering LS pins L4 access (e.g. only `api → db:5432`)

---

## Use case 2 — shared services VPC

```
                     ┌──────────────┐
                     │ VPC-shared   │  (DNS, LDAP, monitoring)
                     │ 172.16.0.0/16│
                     └──┬──┬──┬─────┘
                        │  │  │
       ┌────────────────┘  │  └────────────────┐
       ▼                   ▼                   ▼
  VPC-team-a           VPC-team-b          VPC-team-c
```

- One mesh group, hub-and-spoke usage by ACL on `VPC-shared` side
- Spokes don't need to know each other (ACL denies spoke-to-spoke at peering
  ingress on `VPC-shared` rules — even though L2/L3 reachability is mesh)
- Adding a fourth team is a single `createMeshNetwork` call

---

## Use case 3 — disaster recovery / cross-zone

```
        Zone Z1                          Zone Z2
   ┌──────────────────┐             ┌──────────────────┐
   │ VPC-prod-active  │ ── peer ──> │ VPC-prod-standby │
   └──────────────────┘             └──────────────────┘
```

- **Same VPC name and CIDR** allowed (different VPC IDs)
- Replication traffic (DB streaming, S3 sync, K8s control plane) runs on
  private link-local infra — no public Internet, no SNAT
- **`disableMeshNetwork`** on the standby side caps egress without losing
  topology — useful for blue/green or controlled fail-back

---

## Use case 4 — dev / staging / prod isolation

```
                ┌────────────────┐
                │ VPC-build-tools│   peer-A ──> VPC-dev
                └─────┬──────────┘   peer-B ──> VPC-staging
                      │              peer-C ──> VPC-prod  (disabled by default)
```

- One artifact-server VPC reachable from each environment
- The prod peering stays in **Disabled** state until a release window:
  `enableMeshNetwork` opens it for the deploy, `disableMeshNetwork` shuts it
  again — auditable through API logs

---

## Operational concerns

| Concern | Mechanism |
|---|---|
| Idempotent reconciliation | `provisionMeshNetwork` rebuilds from DB on demand |
| Bulk delete safety | external_id tags + per-pool LL IP detection |
| Restart cleanup | `OvnElement.startup()` re-runs provision for each Active group |
| Permission boundary | account ownership check + non-admin allowed |
| Quota / addressing | /30 slots: 63 same-zone, 63 cross-zone per group |
| Observability | `ovn-nbctl lr-route-list cs-vpc-<id>`, `lr-policy-list`, `ovn-ic-nbctl ts-list` |
| Disable for ACL drift | `disableMeshNetwork` lets you re-stage rules off the wire |

---

## What's next

- **Distributed firewall log shipping** — OVN sample-driven logging ⇒ Loki/ES
- **BGP egress** — replace static defaults with dynamic upstream
- **IPv6 dual-stack** in the peering pools
- **Live ACL hot-swap** without disable/enable churn
  (currently re-applied on every `updateMeshNetwork`)
- **UI**: per-member traffic counters from `Logical_Flow` stats

Plus the plain-old debt: tests against a containerised OVN-NB, telemetry
hooks, and an upstream-friendly split between `cloudstack-server` and the
plugin jar.

---

<!-- _class: lead -->

# Q & A

Repository:
`apache/cloudstack` — branch `ovn-vpc-peering`

Plugin entry-point:
[`OvnElement.java`](../src/main/java/org/apache/cloudstack/service/OvnElement.java)

Schema:
[`schema-42210to42300.sql`](../../../../engine/schema/src/main/resources/META-INF/db/schema-42210to42300.sql)
