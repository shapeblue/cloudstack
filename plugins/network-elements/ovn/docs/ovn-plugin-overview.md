# OVN Plugin for Apache CloudStack

> **Audience.** Cloud engineers who already understand CloudStack's networking
> primitives (Networks, VPCs, Public IPs, Network ACLs) and want to learn how
> the OVN plugin re-implements the data plane *without* the legacy virtual
> router (VR), how guest networks are wired into OVN at the object level,
> what services it currently delivers, and how the new VPC Peering subsystem
> fits in.

> **Source of truth.** Plugin lives at
> `plugins/network-elements/ovn/` in the Apache CloudStack tree
> (branch `ovn-qos-bandwidth` at the time of writing). Schema additions are
> in `engine/schema/src/main/resources/META-INF/db/schema-42210to42300.sql`.

---

## 1. Background

### 1.1 What CloudStack already does

CloudStack abstracts compute, storage, and **networking** into tenant-facing
objects:

| Object | Concept |
|---|---|
| Zone | A region or data centre |
| Pod / Cluster / Host | Physical infrastructure under a Zone |
| Network | A guest L2/L3 segment (Isolated, Shared, or L2-only) |
| VPC | A tenant-private routing domain that contains one or more tier networks |
| NIC | A virtual NIC of a VM, bound to a Network |
| Public IP | An IPv4 from the public-network range, mapped to a tenant resource |
| Network ACL | Stateful filter applied to a VPC tier |
| Static Route, Site-to-Site VPN, … | VPC-scoped extras |

Each Network is offered through a **Network Offering** (e.g. *Isolated with
Source-NAT, DHCP, DNS, ACL*). Network offerings declare which **Network
Element** implements which **Service** for that network. The VR is the
default network element.

### 1.2 Why a new network element

The VR is a tenant-side Linux VM that runs DHCP, DNS, SNAT, port-forwarding,
LB, ACLs, and VPN in user-space. It is well-understood, but it has known
trade-offs:

| Concern | VR | What we want |
|---|---|---|
| Failure domain | one VM per network/VPC | hypervisor-distributed |
| HA story | active/standby pair, takes seconds | sub-second, distributed |
| New service | new image, new VR template, new package | configuration row in a database |
| Throughput | bounded by the VR VM | bounded by OVS flow-table |
| Diagnostics | tcpdump, conntrack, iptables on the VR | NB rows + Logical_Flow trace |

OVN — Open Virtual Network — solves these by moving the L2/L3 logic into a
**logical-network database** (the Northbound DB) which is compiled into
hypervisor-local OVS flows by `ovn-controller`. The OVN plugin's job is to
translate CloudStack's user-facing actions into rows in that database, so
the VR is never required for OVN-backed networks.

### 1.3 Plugin goals & non-goals

**Goals.**

- Replace the VR for the most common service set: connectivity, DHCP, DNS,
  SourceNAT, StaticNAT, PortForwarding, Network ACLs, Firewall, and per-NIC
  egress QoS. The QoS coverage is currently egress-only at the NIC level
  and can be broadened in future revisions.
- Keep the operator's mental model intact: a Network offering still
  declares which services are provided. The plugin announces itself as a
  provider for those services through `OvnElement.getCapabilities()`.
- Enable scenarios the VR cannot model cleanly — most importantly,
  user-driven VPC peering, including across zones via OVN
  Interconnection.

**Non-goals.**

- Wholesale replacement of the VR for *every* offering. LB, Site-to-Site
  VPN, and IPv6 dual-stack remain on the VR side; mixed offerings keep the
  VR for those services.
- Re-implementing OVN itself. The plugin is a thin orchestrator on top of
  the upstream OVN.

---

## 2. Architectural overview

### 2.1 The data path at a glance

```
                       MANAGEMENT PLANE
+---------------------------+    OVSDB JSON-RPC     +-------------------+
| CloudStack Management Srv |  ------------------>  |   OVN NB DB       |
|                           |                       |  (per zone)       |
|  +---------------------+  |    OVSDB JSON-RPC     +---------+---------+
|  |   OvnElement        |--+ <------(read)------------------ | ovn-northd
|  |   (NetworkElement)  |  |                                  v
|  +---------------------+  |                       +-------------------+
|             ^             |                       |   OVN SB DB       |
|             |             |                       |  (per zone)       |
+-------------+-------------+                       +---------+---------+
              |                                                |
              |                                                v
              |                                       +-----------------+
              |                                       | ovn-controller  | <- on each
              |                                       |  + OVS bridge   |    hypervisor
              |                                       +-----------------+
              |                                                |
              |                                                v
              |                                          DATA PLANE
              v                                       (VM tap → OVS → NIC)
        DB / Schema
        (cloud schema +
         ovn_providers,
         ovn_vpc_peerings)
```

The Management Server is the only writer to the **OVN Northbound** database.
All other components are read-only consumers of NB or producers of compiled
state in SB. The plugin **never** drives `ovn-controller` directly and
**never** shells out to `ovn-nbctl` — every operation is an OVSDB JSON-RPC
transaction issued by `OvnNbClient.java`.

### 2.2 Per-zone scope

OVN is fundamentally per-deployment: one NB + one SB per administrative
domain. CloudStack's natural administrative domain is a **Zone**. The plugin
follows that grain: each zone has its own `ovn_providers` row that pins the
NB/SB connection strings and mTLS material.

```
ovn_providers
+----+---------+--------------------------+----------------------+
| id | zone_id | nb_connection            | sb_connection        |
+----+---------+--------------------------+----------------------+
|  2 |       7 | ssl:10.0.34.51:6641      | ssl:10.0.34.51:6642  |
|  3 |       8 | ssl:10.0.35.106:6641     | ssl:10.0.35.106:6642 |
+----+---------+--------------------------+----------------------+
```

This means a tenant's Network or VPC always lives in *exactly one* zone-
scoped NB. The cross-zone story is handled by **OVN Interconnection** —
described in §6 — not by sharing one NB across zones.

### 2.3 Plugin layout

```
plugins/network-elements/ovn/src/main/java/org/apache/cloudstack/
├── api/
│   ├── command/      # AddOvnProviderCmd, ListOvnProvidersCmd,
│   │                 # CreateVpcPeeringCmd, EnableVpcPeeringCmd, ...
│   └── response/     # OvnProviderResponse, VpcPeeringResponse, ...
├── service/
│   ├── OvnElement.java          # NetworkElement implementation
│   ├── OvnNbClient.java         # OVSDB JSON-RPC wrapper
│   ├── OvnProviderServiceImpl.java
│   └── OvnPeeringService.java   # Service interface for peering APIs
└── resources/applicationContext.xml

engine/schema/src/main/java/com/cloud/network/
├── element/OvnVpcPeeringVO.java
└── dao/OvnVpcPeeringDao{,Impl}.java
```

`OvnElement` is the public Spring bean that registers as a CloudStack
`NetworkElement`, `IpDeployer`, `DhcpServiceProvider`, `DnsServiceProvider`,
and friends. Almost every interesting hook the plugin reacts to enters
through one of those interfaces.

---

## 3. OVN concepts cheat-sheet

OVN's vocabulary is small but unfamiliar at first. Below is the subset that
appears in the plugin code and in the rest of this document.

| Term | Full name | Role |
|---|---|---|
| **NB** | Northbound DB | High-level intent — what the network *should* look like (LS, LR, ACL, NAT). The plugin writes here. |
| **SB** | Southbound DB | Compiled flows + chassis state. `ovn-northd` translates NB → SB; `ovn-controller` reads SB and programs OVS. |
| **IC-NB / IC-SB** | Interconnection NB / SB | Global DBs shared across zones. Hold `Transit_Switch` rows and routes learned across zone boundaries. |
| **LS** | Logical_Switch | An L2 broadcast domain. CloudStack Network → one LS. |
| **LSP** | Logical_Switch_Port | A port on an LS. May represent a NIC, a router-side connector, a `localnet` uplink, or a peer attachment. |
| **LR** | Logical_Router | An L3 router. CloudStack VPC → one LR (`cs-vpc-<id>`). For non-VPC isolated networks, one LR per network (`cs-router-<networkId>`). |
| **LRP** | Logical_Router_Port | A router interface. Pairs with an LSP on an attached LS. Carries IP, MAC, and references its peer. |
| **DGP** | Distributed Gateway Port | An LRP that owns external IPs and runs centralised NAT/ARP on a `gateway_chassis`. |
| **NAT** | NAT row on an LR | One of `snat`, `dnat`, or `dnat_and_snat`. CloudStack public IPs and port-forwards become rows here. |
| **TS** | Transit_Switch | An LS in IC-NB that interconnects LRs across zones. The plugin uses one TS per cross-zone peering group. |
| **ACL** | ACL row on an LS or LR | Stateful filter (`from-lport` / `to-lport`). Per-tier and per-peering rules live here. |
| **`localnet`** | LSP type | Bridges an LS to a physical OVS bridge via `ovn-bridge-mappings`. The provider-side of public/external connectivity. |
| **Chassis** | Chassis row in SB | A hypervisor running `ovn-controller`. `gateway_chassis` references pin DGPs to specific chassis with priorities. |

A few related terms that the document does not use but a reader might
encounter in `ovn-sbctl` output: `Logical_Flow` (a SB flow rule),
`Encap` (geneve/STT/vxlan tunnel info), and `Port_Binding` (which chassis
currently owns a logical port).

---

## 4. CloudStack ↔ OVN object mapping

The plugin uses **deterministic naming** so reconciliation is idempotent —
every OVN object name can be derived from CloudStack IDs alone. After a
restart-with-cleanup, the same names produce the same OVN topology without
any orphans.

| CloudStack object | OVN object | Name pattern |
|---|---|---|
| Isolated Network (non-VPC) | Logical_Switch + Logical_Router | `cs-net-<networkId>`, `cs-router-<networkId>` |
| VPC | Logical_Router | `cs-vpc-<vpcId>` |
| VPC tier (Network in a VPC) | Logical_Switch | `cs-net-<networkId>` (router is the VPC's LR) |
| Tier-to-router junction | LRP + LSP | `lrp-cs-net-<networkId>`, `lsp-cs-vpc-<vpcId>` |
| NIC | Logical_Switch_Port (NIC type) | `<nicUuid>` |
| Public IP (SourceNAT) | NAT row (`snat`) | indexed by VPC, attached to DGP |
| Public IP (StaticNAT / Floating) | NAT row (`dnat_and_snat`) | one per public IP |
| Port forward rule | NAT row (`dnat_and_snat` with `external_port`) | one per rule |
| Network ACL list | ACL rows on the tier LS | filtered by `external_ids:cloudstack_acl_id` |
| VPC peering member (same zone) | LRP on VPC LR + LSP on peering LS | `lrp-peer-<groupUuid>-vpc-<vpcId>`, `lsp-peer-<groupUuid>-vpc-<vpcId>` |
| VPC peering member (cross zone) | LRP on VPC LR + LSP on Transit_Switch | `lrp-cs-vpc-<vpcId>-ts`, `lsp-ts-vpc-<vpcId>` |
| DHCP server for a tier | DHCP_Options row | indexed by network external_id |
| Per-VM egress rate-limit | LSP `options:qos_max_rate` + `qos_burst` | applied to NIC LSP |

The plugin **does not** store these names in the CloudStack DB. The names
are reconstructed on demand from the CloudStack object IDs, which keeps the
OVN-NB the single source of truth for OVN state and the CloudStack DB the
single source of truth for tenant intent.

---

## 5. Networks and tiers

### 5.1 Isolated network (no VPC)

The simplest case is an isolated network not attached to any VPC. The
plugin builds:

```
        +---------------------+
        | LR  cs-router-<id>  |
        |   (gateway IP)      |
        +---------+-----------+
                  | LRP  lrp-cs-net-<id>
                  | (gateway IP, /prefix)
                  v
        +---------+-----------+
        | LS  cs-net-<id>     |
        +---------+-----------+
            |     |     |
           NIC   NIC   NIC      (LSPs per VM NIC)
```

If the offering grants `SourceNat`, the LR also gets a **distributed gateway
port** to a provider LS that bridges to the public physical network via a
`localnet` LSP — see §7.

### 5.2 VPC tier

A VPC has one shared LR (`cs-vpc-<vpcId>`). Each tier (a Network whose
`vpc_id` is set) is an LS attached to that LR by an LRP/LSP pair:

```
                         +------------------------+
                         | LR  cs-vpc-<vpcId>     |
                         |   (CIDR aggregate)     |
                         +-----+------+-----------+
                               |      |
                               |      |   (one LRP per tier)
                               v      v
              +-------------+         +-------------+
              | LS web-tier |         | LS db-tier  |
              | 10.0.10/24  |         | 10.0.20/24  |
              +-------------+         +-------------+
                  |    |                |    |
                 NIC  NIC              NIC  NIC
```

Tier-to-tier traffic flows through the VPC's LR. Per-tier ACLs apply at the
LS where the destination tier hangs (and conversely on the source LS for
egress). NAT for outbound Internet traffic happens at the LR's DGP.

### 5.3 Naming determinism

All names are derived from numeric CloudStack IDs. There is no
`getName()`-based naming because users can rename Networks. The IDs are
stable for the lifetime of the object. If a CloudStack object is deleted
and a new one created, it gets a new ID and therefore a fresh OVN object;
the old OVN object is removed by the lifecycle hooks before deletion.

---

## 6. Multi-zone via OVN-IC

OVN itself is per-deployment, but **OVN Interconnection** (the
`ovn-ic` daemon plus the IC-NB / IC-SB databases) lets independent OVN
deployments — i.e. zones — exchange routes through a global *Transit
Switch*.

```
            Zone Z1 NB                                Zone Z2 NB
       +------------------+                       +------------------+
       |  cs-vpc-A   ─┐   |                       |   ┌── cs-vpc-D  |
       |  cs-vpc-B  ─┤    |                       |   │             |
       +─────────────┼────+                       +───┼─────────────+
                     │                                │
                     ▼                                ▼
                 +────────────────────────────────────────+
                 │           ts-peer-<groupUuid>          │      ◄── IC NB
                 │                (Transit Switch)        │
                 +────────────────────────────────────────+
                                     ▲
                                     │ ic-route-adv / ic-route-learn
                                     │
                              +─────────────────+
                              │  ovn-ic daemon  │   (one per zone, peers
                              +─────────────────+    over the IC SB)
```

The plugin stores `ic_nb_connection` on the `ovn_providers` row. Zones
that participate in cross-zone topologies must point at the same global
IC-NB. Cross-zone VPC peering (§9.2) is the first feature to consume this
plumbing.

---

## 7. External connectivity & gateways

For each VPC LR — and for isolated-network LRs that grant SourceNAT —
the plugin sets up an egress path that gives VMs Internet access through
provider-network IPs while still running the bulk of L3 logic
distributed.

### 7.1 Distributed gateway port

The DGP is a special LRP attached to the **provider LS**, an LS that has
a `localnet` LSP bridging to the physical OVS bridge via
`ovn-bridge-mappings`.

```
   ┌──────────────────────┐                 ┌────────────────────┐
   │  LR cs-vpc-<vpcId>   │                 │ LS provider-public │
   │                      │                 │                    │
   │    (DGP) lrp-pub  ───┼─────────────────┼─── lsp-lrp-pub     │
   │                      │                 │                    │
   │    NAT (snat,        │                 │  localnet ─── physical bridge
   │         dnat_and_snat)                 │   (br-ex / br-eth1 / …)
   └──────────────────────┘                 └────────────────────┘
```

ARP for the public IP set is the responsibility of the chassis the DGP is
**pinned** to. In OVN's terminology this is the `gateway_chassis` set on
the LRP — a priority list. When the highest-priority chassis is
unreachable, OVN's `northd` re-elects the next; the new owner emits a
gratuitous ARP so upstream switches re-learn.

The plugin walks the SB to find chassis whose `other_config:ovn-bridge-
mappings` contains the relevant physical mapping (and skips remote-AZ
chassis when applicable) and sets the gateway_chassis list with explicit
priorities.

### 7.2 NAT translations

Public-IP types map to OVN NAT rows:

| CloudStack public-IP type | OVN NAT type | Notes |
|---|---|---|
| Source NAT | `snat` | one row per VPC; `external_ip` is the SNAT IP, `logical_ip` is the VPC CIDR |
| Static NAT (1:1 floating) | `dnat_and_snat` | `external_ip` is the public IP, `logical_ip` is the VM private IP |
| Port forward | `dnat_and_snat` | `external_port`/`logical_port` set; one row per rule |

`gARP-on-NAT-change` is on by default; when a NAT row is created or the
gateway pin moves, the new owner chassis announces.

### 7.3 NAT bypass for peering

VPC peering (§9) deliberately avoids the DGP/NAT path: traffic between
peered VPCs is supposed to keep its private addressing. The plugin
achieves this with `Logical_Router_Policy` rows at priority 1000 with
`reroute` action that match destinations inside any peered CIDR — these
policies are evaluated *before* the SNAT rule and divert traffic into
the peering port.

---

## 8. Services delivered

`OvnElement.getCapabilities()` declares which services the plugin
implements. As of `ovn-qos-bandwidth`:

| CloudStack Service | Capability | OVN object that backs it |
|---|---|---|
| Connectivity | `StretchedL2`, `RegionLevel` | LS + LR |
| DHCP | server-side | DHCP_Options row referenced from each NIC's LSP |
| DNS | server-side | the same DHCP_Options row carries `dns_server` |
| UserData | ConfigDrive | hypervisor cloud-init ISO (zone-level), not OVN |
| SourceNat | redundant | LR `nat:snat` + DGP |
| StaticNat | per-IP | LR `nat:dnat_and_snat` |
| PortForwarding | TCP/UDP | LR `nat:dnat_and_snat` with port mapping |
| NetworkACL | per tier | LS `acls` rows scoped by `external_ids` |
| Firewall | egress + ingress | LR/LS `acls` |
| QoS | per-NIC egress | LSP `options:qos_max_rate` + `qos_burst` |

**Not** delivered by the OVN plugin in the current branch: Load Balancer
(LB), Site-to-Site VPN, IPv6 dual-stack, multicast. Network offerings
that require those services keep the VR as the provider for them.

### 8.1 DHCP and DNS

Each tier LS gets a single DHCP_Options row (idempotently created on the
first NIC `prepare`) carrying:

```
server_id  = <gateway IP of the tier>
router     = <gateway IP of the tier>
server_mac = locally-administered MAC derived from the network ID
lease_time = 86400
mtu        = 1442  (geneve overhead headroom)
dns_server = [network.dns1, network.dns2]
```

Each NIC LSP is then linked to that DHCP_Options row via the
`dhcpv4_options` column. ovn-controller answers the DHCPDISCOVER on the
hypervisor that hosts the VM — there is no DHCP packet leaving the host.

### 8.2 QoS — per-NIC egress rate-limit

When a VM is started or migrated, `prepare(nic)` consults the VM's
**service offering** for `nw_rate` (Mbps). If positive, it writes:

```
Logical_Switch_Port.options:qos_max_rate = nw_rate * 1000   (kbps)
Logical_Switch_Port.options:qos_burst    = max(nw_rate * 100, 12)  (kbits)
```

OVN treats `qos_max_rate` on an LSP as the meter for **traffic ingressing
the switch from that port** — i.e. VM upstream / egress. The 100-ms burst
gives TCP slow-start enough room to ramp without spurious drops.

The current scope is egress only and the rate is read from the service
offering's `nw_rate`. Per-tier rate, ingress shaping, DSCP marking, and a
fallback to the `vm.network.throttling.rate` global setting are candidates
for future revisions.

---

## 9. VPC Peering subsystem

CloudStack VPCs are isolated routing domains. Tier-to-tier traffic stays
inside a VPC. **Cross-VPC** traffic on the legacy stack has to leave one
VPC, traverse the public network, and re-enter the other through Static
NAT — which breaks private addressing and ACL boundaries.

The OVN plugin introduces a dedicated **VPC Peering** subsystem that
builds a private peering fabric in OVN, bypassing public IPs and SNAT,
and applying ACLs at the peering boundary.

### 9.1 Same-zone peering

When all members of a peering group live in the same zone, the plugin
provisions a zone-local **peering Logical_Switch** and attaches each
VPC's LR to it via /30 link-local subnets:

```
                     ┌── peering LS ───────────────────────┐
                     │   cs-peer-<groupUuid>               │
                     └──┬───────────────┬──────────────┬───┘
                        │ .1/30         │ .5/30        │ .9/30
                ┌───────┴───┐  ┌────────┴───┐  ┌───────┴───┐
                │ cs-vpc-A  │  │ cs-vpc-B   │  │ cs-vpc-C  │   logical routers
                └───────────┘  └────────────┘  └───────────┘
```

Addressing is from `169.254.100.0/24` (the *same-zone* pool — see §9.3).
Each LR gets a static route per peer CIDR pointing at the peer's link-
local IP, plus a `Logical_Router_Policy` at priority 1000 with `reroute`
that bypasses SNAT for destinations inside any peered CIDR.

### 9.2 Cross-zone peering

When the group spans zones, the same logic moves into the IC-NB through
a **Transit_Switch** named `ts-peer-<groupUuid>`:

```
        Zone Z1                                       Zone Z2
   ┌──────────────────────┐                     ┌──────────────────────┐
   │  cs-vpc-A   ─┐       │                     │       ┌── cs-vpc-D   │
   │  cs-vpc-B  ─┤        │                     │       │              │
   └─────────────┼────────┘                     └───────┼──────────────┘
                 │   ┌────────────────────────────┐    │
                 └──>│ ts-peer-<groupUuid>        │<───┘
                     │  (Transit_Switch in IC-NB) │
                     └────────────────────────────┘
```

Addressing for cross-zone members comes from a **separate** /24 pool —
`169.254.200.0/24` — so the two fabrics never alias if a VPC is in a
group that grew from same-zone to cross-zone (or shrunk back). The
encoding lets the plugin tell *from the link-local IP alone* whether a
member belongs to a same-zone or cross-zone fabric, which matters for
deletion correctness (see §9.5).

OVN-IC's `ic-route-adv` advertises each LR's local CIDR onto the TS, and
`ic-route-learn` installs the peer routes on the LRs in the other zones —
no static routes, no manual route-advertise calls.

### 9.3 Per-member objects

For each peering record (one VPC's slot in a group), the plugin creates:

| Object | External_id tag | Purpose |
|---|---|---|
| LRP on `cs-vpc-<vpcId>` | `cloudstack_peering_group=<groupUuid>` | Router-side port into the peering LS or TS |
| LSP on peering LS or TS | `cloudstack_peering_group=<groupUuid>` | Counterpart of the LRP |
| Static route per peer CIDR | `cloudstack_peering_group=<groupUuid>` | Forwards traffic to the peer's link-local IP (same-zone only; cross-zone uses learned routes) |
| LR Policy `reroute` priority 1000 | `cloudstack_peering_group_target=<peerVpcId>` | Skips SNAT for peered CIDRs |
| ACL rows on the peering LS | `cloudstack_peering_acl_vpc_<vpcId>=true` | Apply this VPC's `aclid` filter to its peering traffic |

The `external_ids` strategy means **bulk cleanup never has to track
individual UUIDs in the CloudStack DB.** A delete or disable removes every
row whose `external_ids` matches a (group, vpc) pair through helper
methods like `removeStaticRoutesByExternalId` and
`removeLogicalRouterPoliciesByExternalId` on the NB client.

### 9.4 Persistence

```sql
CREATE TABLE ovn_vpc_peerings (
    id              bigint unsigned auto_increment PRIMARY KEY,
    uuid            varchar(40)  NOT NULL UNIQUE,
    group_uuid      varchar(40)  NOT NULL,    -- group identifier (UUID for the mesh)
    vpc_id          bigint       NOT NULL,    -- one row per VPC in the group
    zone_id         bigint       NOT NULL,
    account_id      bigint       NOT NULL,
    domain_id       bigint       NOT NULL,
    link_local_ip   varchar(15)  NOT NULL,    -- pool prefix encodes same vs cross zone
    acl_id          bigint       NULL,        -- per-member ACL applied at peering boundary
    name            varchar(255) NULL,
    description     varchar(255) NULL,
    state           varchar(20)  NOT NULL,    -- Active | Disabled | Removed
    created         datetime     NOT NULL,
    removed         datetime     NULL
);
```

There is **one row per VPC** in a peering mesh. The natural grouping key
is `group_uuid`. The link-local IP is stored explicitly because (a) the
pool prefix is the cross-zone signal, and (b) the slot must remain
reserved while a member is `Disabled`, so a re-enable picks the same
address.

### 9.5 API surface

| Command | Role | Body |
|---|---|---|
| `createVpcPeering` | Add a VPC to a group; the very first call seeds the group | `{name, vpcid, peervpcid, [aclid], [description]}` |
| `listVpcPeerings` | Returns aggregated **groups** with `members[]` embedded | `{vpcid?, groupuuid?}` |
| `updateVpcPeering` | Change a member's ACL | `{id, aclid?}` |
| `enableVpcPeering` | Re-provision the OVN data plane for a Disabled group | `{id}` (group UUID or any member UUID) |
| `disableVpcPeering` | Tear down the OVN data plane, keep DB rows | `{id}` |
| `deleteVpcPeering` | Remove a member, or the whole group | `{id}` (peering-uuid or group-uuid) |

All commands are authorized for the **User** role — no admin gate. Adding
a VPC to a group works through `peervpcid`: if the peer is already in a
group, the new caller joins that `group_uuid` instead of starting a new
mesh.

The aggregated `listVpcPeerings` response is what the CloudStack UI's
AutogenView consumes: one row per group, with each row carrying the full
member list under `members[]` so the per-VPC tab can be rendered without a
second round-trip.

### 9.6 State machine

```
                ┌──────────┐  enableVpcPeering   ┌──────────┐
                │ Disabled │ ──────────────────> │  Active  │
                └────┬─────┘ <───────────────────└────┬─────┘
                     │       disableVpcPeering        │
                     │                                │
                     │  deleteVpcPeering              │  deleteVpcPeering
                     ▼                                ▼
                ┌────────────────────────────────────────┐
                │              Removed                   │
                └────────────────────────────────────────┘
```

- **Active** — full OVN fabric in place. Routes advertised, ACLs applied,
  NAT bypass policies live.
- **Disabled** — DB row + link-local IP still reserved. The OVN data plane
  for this group is torn down (LRPs, LSPs, peering LS or TS attachments,
  routes, policies, ACLs all removed). `enableVpcPeering` is idempotent
  and rebuilds via `provisionPeeringGroup`.
- **Removed** — terminal. The link-local slot is freed. A new
  `createVpcPeering` may reuse the slot.

### 9.7 Constraints enforced at create time

`createVpcPeering` rejects the request before any OVN row is touched if any
of these hold:

- The two VPCs are the same VPC (`vpcid == peervpcid`).
- The caller does not own both VPCs.
- The two VPCs belong to different accounts.
- Either VPC's zone has no `ovn_providers` row.
- The two VPCs already belong to *different* peering groups (a VPC may only
  be in one group at a time).
- An `aclid` was supplied that doesn't belong to the VPC the caller is
  operating on.
- **The two VPCs (or any existing group member) carry IPv4 CIDRs that
  overlap.** The peering fabric writes one static route per peer CIDR on
  every member's LR; two routes for overlapping prefixes would race and
  OVN cannot disambiguate at runtime. Disabled members are included in
  this check so re-enabling the group cannot produce overlap either.

### 9.8 Operational invariants

A few invariants worth knowing when reading the code or debugging:

- **Cross-zone detection is by IP prefix, not by member-set inspection.**
  Bulk delete iterates members and the Active set shrinks at each step;
  trying to detect cross-zone from "are there still members in two
  zones?" gives a wrong answer once you're processing the second-to-last
  member. The link-local IP (`169.254.200.x` ⇒ cross-zone) is stable
  through the whole delete.
- **Provisioning is fully idempotent.** `provisionPeeringGroup(uuid)` can
  be called any number of times; it computes the desired set and calls
  the NB client which short-circuits when the requested state already
  matches the current state. This makes restart-with-cleanup and
  enable-after-disable cheap.
- **`external_ids` are the only delete handle.** Static routes and LR
  policies are not tracked individually in CloudStack DB. The delete
  path removes every NB row whose external_ids match the
  (`cloudstack_peering_group`, `<groupUuid>`) tuple. Add new tagged
  rows and update the cleanup queries accordingly when extending the
  feature.

---

## 10. Lifecycle hooks

The plugin reacts to CloudStack lifecycle events through `NetworkElement`
interface methods. The interesting ones:

| Hook | When | What the plugin does |
|---|---|---|
| `implement(network)` | First VM in a network is being deployed, or restart-with-cleanup runs | Ensures the LR exists, creates the tier LS, attaches LS↔LR via LRP+LSP, programs DHCP, ensures egress SNAT and DGP if the offering grants SourceNAT |
| `prepare(nic, vm)` | NIC about to plug into the data plane | Creates the NIC LSP, links DHCP options, **applies QoS** |
| `release(nic, vm)` | NIC unplugging | Removes the NIC LSP |
| `shutdown(network)` / `shutdownVpc(vpc)` | Network/VPC being torn down | Cascading cleanup of all OVN rows for the network/VPC |
| `applyIps`, `applyStaticNats`, `applyPortForwards`, `applyAcls` | User-facing API call modifies a Public IP / NAT / ACL | Reconciles the corresponding NAT or ACL rows |
| `startup()` | MS boot | Walks active VPC peering groups and re-runs `provisionPeeringGroup` to recover from any drift introduced while the MS was down |

All hooks are written to be safe to re-run. The plugin does **not**
maintain a "what was last seen" cache; it always reads CloudStack DB +
OVN-NB and converges.

---

## 11. Use cases for VPC Peering

These are the four scenarios the peering subsystem was designed to cover.
They are also the four scenarios used to validate the design end-to-end
on a lab.

### 11.1 Multi-tier app spanning two VPCs

```
   VPC-app  (10.0.0.0/16)            VPC-data  (10.1.0.0/16)
     ├─ web       10.0.10.0/24        ├─ db          10.1.10.0/24
     ├─ api       10.0.20.0/24        ├─ cache       10.1.20.0/24
     └─ <peer>───────────────────────────<peer>
```

Decouples app and data lifecycles into separate VPCs (often separate
account ownership). DB tier never gets a public IP. Per-VPC ACL on the
peering LS pins L4 access (e.g. `api → db:5432` only).

### 11.2 Shared services hub

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

One mesh group, hub-and-spoke usage by ACL on the `VPC-shared` side.
Spokes don't need to know each other (the ACL on `VPC-shared`'s peering
boundary denies spoke-to-spoke even though L2/L3 reachability technically
exists across the mesh).

### 11.3 Cross-zone DR

```
        Zone Z1                          Zone Z2
   ┌──────────────────┐             ┌──────────────────┐
   │ VPC-prod-active  │ ── peer ──> │ VPC-prod-standby │
   └──────────────────┘             └──────────────────┘
```

Replication traffic (DB streaming, S3 sync, K8s control plane) over the
private OVN-IC fabric. `disableVpcPeering` on the standby side caps egress
without losing topology — useful for blue/green or controlled fail-back.

### 11.4 Dev / staging / prod with disabled-by-default prod peering

```
                ┌────────────────┐
                │ VPC-build-tools│   peer-A ──> VPC-dev
                └─────┬──────────┘   peer-B ──> VPC-staging
                      │              peer-C ──> VPC-prod  (Disabled by default)
```

One artifact-server VPC reachable from each environment. The prod peering
stays Disabled until a release window: `enableVpcPeering` opens it for
the deploy, `disableVpcPeering` shuts it again — both auditable through
the standard CloudStack API logs.

---

## 12. Operational notes

### 12.1 Observability

The most useful commands for poking around an OVN fabric driven by the
plugin:

| What | Command |
|---|---|
| Inventory of LSes / LRs in a zone | `ovn-nbctl show` |
| Ports on an LR | `ovn-nbctl lrp-list cs-vpc-<id>` |
| Static routes on an LR | `ovn-nbctl lr-route-list cs-vpc-<id>` |
| Policies on an LR | `ovn-nbctl lr-policy-list cs-vpc-<id>` |
| NAT rows on an LR | `ovn-nbctl lr-nat-list cs-vpc-<id>` |
| LSP options (incl. QoS) | `ovn-nbctl --columns=name,options find logical_switch_port name="<nic-uuid>"` |
| ACLs on an LS | `ovn-nbctl acl-list <ls-name>` |
| Cross-zone Transit Switches | `ovn-ic-nbctl ts-list` |
| Where is a logical port bound | `ovn-sbctl show` (Port_Binding section) |
| Trace a flow | `ovn-trace --minimal <ls-name> 'inport=="<lsp>" && eth.src==... && ip4.src==... && ...'` |

On the management side, structured search through the management server
log is your friend. The plugin uses logger names rooted at
`org.apache.cloudstack.service.OvnElement`; an info-level grep against
that prefix returns most of the per-operation summary (e.g.
`Applied QoS to LSP [...]`, `Provisioned peering group [...]`).

### 12.2 Known failure modes

| Symptom | Likely cause | Where to look |
|---|---|---|
| `/client/api` returns 404 after a UI deploy | `WEB-INF/web.xml` was wiped by an `rsync -a --delete` from `ui/dist/` | Restore WEB-INF from the most recent timestamped backup of `webapp.dir` |
| New VPC peering does not propagate cross-zone | `ovn-ic` not running, or zones don't share an IC-NB | `ovn-ic-nbctl ts-list` on each zone host; check `ovn_providers.ic_nb_connection` |
| Stale routes remain on a VPC LR after delete | `external_ids` mismatch on the cleanup query | Inspect `lr-route-list cs-vpc-<id>` for routes whose external_ids contain the dead group_uuid; delete by external_id |
| VM cannot reach Internet | DGP gateway_chassis is on a host that lost the physical mapping, or the chassis is unreachable | `ovn-sbctl list chassis`, `ovn-nbctl get logical_router_port lrp-... gateway_chassis` |
| Peering says "removed" in DB but VMs still talk | Cross-zone delete bug fixed in `OvnElement` (use IP-prefix detection); leftover TS/LRPs may remain on a deployment that hit the bug — manual cleanup via `ovn-nbctl --if-exists lrp-del` and `ovn-ic-nbctl ts-del` is required once |

### 12.3 Capacity

Same-zone peering pool: `169.254.100.0/24` divided into /30 subnets gives
**63 slots** per group (252 host bits / 4). The cross-zone pool
(`169.254.200.0/24`) provides another 63. A single CloudStack
deployment can host arbitrary many groups; the limit is per-group only.

Number of NIC LSPs per zone is bounded by OVN's flow-table scalability
(rather than CloudStack's). Real-world OVN deployments have run with
high tens of thousands of LSPs; production planning past that should
consult OVN-side scale reports.

---

## 13. Roadmap

Already in flight or considered for the next branch:

- **Broader QoS coverage.** Per-tier rate (`QoS` table on the LS),
  ingress shaping (OVN's `qos_max_rate` is one-directional on the LSP),
  DSCP marking, and an explicit *unset* path for when an offering's
  rate goes back to null without a stop+start.
- **Distributed firewall logging.** `ACL.log` + sampling driven into
  Loki / Elastic / Grafana for tenant-visible flow logs.
- **BGP egress.** Replace the static default route with a dynamic
  upstream peering for multi-homed deployments.
- **IPv6 dual-stack.** Both for tier networks and the peering pools.
- **Live ACL hot-swap** without a disable/enable churn — currently the
  ACL re-apply is wrapped into `updateVpcPeering`.
- **Per-flow telemetry.** Surface OVN's `Logical_Flow` stats to the
  CloudStack UI per peering or per tier.

Plus the usual debt items: containerised integration tests against an
upstream OVN-NB image, a clean split between core `cloudstack-server`
and the plugin jar, and CI matrices that include the OVN path.

---

## 14. References

- **OVN documentation.** [www.ovn.org](https://www.ovn.org/), the
  `ovn-nb(5)` and `ovn-sb(5)` man pages, and the OVN-IC tutorial in the
  upstream tree.
- **Apache CloudStack docs.** Networking-and-traffic chapter of the
  Admin Guide (for VPC, ACL, Public IP semantics).
- **Plugin source — entry points.**
  - [OvnElement.java](../src/main/java/org/apache/cloudstack/service/OvnElement.java)
  - [OvnNbClient.java](../src/main/java/org/apache/cloudstack/service/OvnNbClient.java)
  - [OvnPeeringService.java](../src/main/java/org/apache/cloudstack/service/OvnPeeringService.java)
- **Schema additions.**
  [`schema-42210to42300.sql`](../../../../engine/schema/src/main/resources/META-INF/db/schema-42210to42300.sql)
- **Companion deck.**
  [`ovn-plugin-deep-dive.md`](./ovn-plugin-deep-dive.md) — the slide
  format of the same content for ~30-minute presentations.
