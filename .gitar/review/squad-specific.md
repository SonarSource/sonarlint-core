# Core review focus

- Trace public API/RPC changes through actual accessible consumers, including serialized requests, responses, defaults, and callbacks.
- Check `API_CHANGES.md`; accept intentional coordinated changes, and flag concrete consumer breaks or missing required coordination.
- Check that significant consumer-facing API/RPC additions, behavior changes, deprecations, breaking changes, and integration requirements are documented in `API_CHANGES.md` under the relevant version/category, following existing entries; omit internal refactors, tests, build changes, and routine dependency bumps without API impact.
- Never edit shipped database migrations or those already on master; add a numbered migration and verify upgrades from existing databases.
