# Storage snapshots

This folder holds a copy of the local H2 storage written by the **latest release** of SonarLint Core.
`StorageUpgradeMediumTests` starts the current backend on it, which is what users experience when they upgrade.
It catches:

- an H2 or Flyway upgrade that cannot read files written by the previous version, after a clean shutdown or after a
  crash while a transaction was open (like H2 2.5.252, which saw an empty database in files left that way by 2.4.240)
- a migration that was edited after it shipped (Flyway rejects the changed checksum at startup)
- data lost during migration

There is only ever one snapshot folder, named after the release tag that wrote it:

```
<release tag>/
  h2/sq-ide.mv.db       the database file, as written by that release
  unclean-shutdown/h2/sq-ide.mv.db
                        the same data, after a crash while a transaction deleting findings was open
  snapshot.properties   release, H2 and Flyway versions, migration checksums and row count per table
  schema.sql            text dump of the schema, to review changes between snapshots
```

`snapshot.properties` and `schema.sql` are what to review when a snapshot is replaced. The `.mv.db` file is binary
and is never byte-for-byte identical between two generations, even when nothing changed.

## Replacing the snapshot after a release

The `Full release` workflow does it for releases from master: it generates the snapshot from the release tag and adds
it to the "Prepare next development iteration" PR. If that fails, the PR says so and the snapshot must be generated
manually.

To generate it manually, from a checkout of the release tag (the generator classes must be present, copy them from
master if the tag is older):

```
TAG=12.3.0.87938
mvn install -DskipTests -pl medium-tests -am -P '!its-linux'
mvn exec:java -pl medium-tests -P '!its-linux' \
  -Dexec.mainClass=mediumtest.storage.StorageSnapshotGenerator -Dexec.classpathScope=test \
  -Dexec.args="$TAG <master checkout>/medium-tests/src/test/resources/storage-snapshots/$TAG"
```

Then delete the previous snapshot folder on master and commit the new one.

## Content

The generator (`StorageSnapshotGenerator`) fills every table with a few rows for connection `conn1`,
project `projectKey` and configuration scope `configScope`. Constraints:

- the upgrade test must declare connection `conn1`, otherwise the backend purges its data at startup
- known findings are stored with the OS path separator, so they use a file without folders to be readable on every OS
- the local-only issue is resolved in the year 2100, because resolved local-only issues are purged one week after
  their resolution

When adding a table, extend the generator so that the next snapshot covers it.

The crash is simulated with `SHUTDOWN IMMEDIATELY`, which stops the database without committing nor closing anything,
right after a `CHECKPOINT SYNC` has written the open transaction to the file. This is deterministic, unlike killing a
process at a random time. The generator then checks that the release's own H2 version recovers all the committed
rows from that file, and fails otherwise.

## Limitations

Only the latest release is covered. Users who skip releases go through older storage stacks that are no longer
tested once a release changes the H2 version, the Flyway version or the migrations.
