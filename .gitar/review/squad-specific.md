# Core review focus

Apply these checks only when the diff touches the relevant behavior.
Leave an advisory finding only for a concrete failure supported by the changed
code and its callers; name the failing scenario and source location.
Update an existing thread for the same concern, preserve unresolved evidence,
and remain quiet when the checks pass. Do not approve, block, or modify the PR.

## Public contracts and consumers

- For public API or RPC changes, trace requests, responses, serialization defaults,
  and client callbacks through `rpc-protocol/` and `client/rpc-java-client/`.
- Check `API_CHANGES.md` and the relevant accessible IDE/client usage. Distinguish
  an intentional, documented, coordinated breaking change from an accidental
  regression; do not demand backward compatibility for every planned API removal.
- Raise a concern only with a concrete consumer or serialized exchange that
  fails, or evidence that the required coordinated consumer change is missing.

## Database upgrades

- For persistence changes, follow existing-database upgrade paths and checksum
  validation, not only fresh database creation. Shipped migrations are immutable:
  `backend/commons/src/main/resources/db/migration/README_BEFORE_TOUCHING_THIS_FOLDER.md`
  requires a new numbered migration rather than editing an existing one.
- Report a reachable upgrade failure, data loss, or changed checksum with evidence.

## Java runtime boundaries

- `client/pom.xml` and `rpc-protocol/pom.xml` currently target Java 11; root
  `pom.xml` defaults to Java 21 for the backend. Recheck these files when relevant.
- For changes crossing this boundary, verify used APIs and dependency bytecode
  against the client runtime. Backend Java 21 support does not permit Java 21-only
  classes or dependencies in Java 11 client/RPC artifacts.
