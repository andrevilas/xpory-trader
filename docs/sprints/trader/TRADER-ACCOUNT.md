# Trader Account Synchronisation

The Trader Account represents the local operational identity for cross-WL trades.
It is provisioned by the Control Plane and materialised on the node by the Policy
Agent job introduced in this change set.

## Flow

1. Control Plane admin approves a Trader Account for a WL (`/wls/{id}/trader`).
2. Policy packages returned by the CP include a `traderAccount` section.
3. The node-side `TraderPolicyJob` polls every ~3 minutes, calls the CP with mTLS
   + JWT, and feeds the payload to `TraderAccountService`.
4. The service masks sensitive contact data before persisting to
   `trader_accounts` and records the originating `correlationId`.
5. When a Trader is created/updated and remains `active`, the node emits a
   `trader.account.confirmed` telemetry event back to the CP.

## Recovery Sync

If a WL is removed from the CP but still has a local Trader Account, an admin
can re-register the WL and trigger a manual sync:

1. Call `POST /wls/{id}/trader/sync` on the CP.
2. The CP fetches `GET {gatewayUrl}/control-plane/trader-account` from the WL.
3. The CP upserts the Trader Account using the `id` returned by the WL to keep
   `cpTraderId` stable.

The local table only stores metadata required for cross-WL trading. All contact
fields are hashed + masked to avoid persisting raw values.

## Related Governance Cache

The WL node also persists accompanying governance caches:

- `cp_policy_cache`: mirror of `import_enabled`, `export_delay_days`, and
  `visibility_wls` per WL (alongside the Trader metadata above).
- `cp_relationship_cache`: FX rates, imbalance limits, and status per WL pair.

Both caches are written only after signature verification succeeds, ensuring the
node only adopts CP-sanctioned governance packages.

### Configuration

- `APP_CP_GOVERNANCE_SIGNING_SECRET`: Shared HMAC secret used to verify
  governance payloads.
- `APP_CP_RELATIONSHIP_PEERS`: Comma-separated list of `src:dst` WL pairs that
  the node should pull relationship packages for (e.g. `wl-a:wl-b,wl-a:wl-c`).
