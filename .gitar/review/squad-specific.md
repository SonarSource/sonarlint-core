# Core review focus

- Check public API/RPC changes for compatibility with client repositories such as `sonarlint-intellij`, `sonarlint-eclipse`, `sonarlint-language-server`, and `sonarlint-visualstudio`, when accessible. Identify required consumer updates and missing coordination for intentional breaking changes.
- Check that significant API/RPC additions, deprecations, breaking changes, and API integration requirements are documented in `API_CHANGES.md`, following existing entries; omit changes with no public API impact.
