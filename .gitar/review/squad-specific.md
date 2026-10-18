# Core review focus

- Trace public API/RPC changes through actual accessible consumers, including serialized requests, responses, defaults, and callbacks.
- Check `API_CHANGES.md`; accept intentional coordinated changes, and flag concrete consumer breaks or missing required coordination.
- Never edit shipped database migrations or those already on master; add a numbered migration and verify upgrades from existing databases.
- Preserve Java 11 compatibility for client/RPC APIs and dependency bytecode; the backend uses Java 21. Recheck relevant POM targets when affected.
- Report only evidenced consumer failures, upgrade/data-loss risks, or runtime incompatibilities, naming the scenario and source location.
