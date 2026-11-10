# Storage snapshots

This folder holds a copy of the local H2 storage written by the **latest release** of SonarLint Core.
`StorageUpgradeMediumTests` starts the current backend on it, which is what users experience when they upgrade.
It catches:

- an H2 or Flyway upgrade that cannot read cleanly closed files written by the previous version
- a migration that was edited after it shipped (Flyway rejects the changed checksum at startup)
- data lost during migration

There is only ever one snapshot folder, named after the release tag that wrote it:

```
<release tag>/
  h2/sq-ide.mv.db       the database file, as written by that release
  snapshot.properties   release, H2 and Flyway versions, migration checksums and row count per table
  schema.sql            text dump of the schema, to review changes between snapshots
```

`snapshot.properties` and `schema.sql` are what to review when a snapshot is replaced. The `.mv.db` file is binary
and is never byte-for-byte identical between two generations, even when nothing changed.

## Replacing the snapshot after a release

This is meant to be done by the release workflow, when preparing the version bump PR. To do it manually, from a
checkout of the release tag (the generator classes must be present, copy them from master if the tag is older):

```
TAG=12.3.0.87938
mvn test -pl medium-tests -am -P '!its-linux' \
  -Dtest=StorageSnapshotGeneratorMediumTests -Dsurefire.failIfNoSpecifiedTests=false \
  -Dsonarlint.storageSnapshot.release=$TAG \
  -Dsonarlint.storageSnapshot.outputDir=<master checkout>/medium-tests/src/test/resources/storage-snapshots/$TAG
```

Then delete the previous snapshot folder on master and commit the new one.

Use `mvn test`, not `mvn install`, so that the build of the tag does not overwrite artifacts in your local repository.

## Content

The generator (`StorageSnapshotGeneratorMediumTests`) fills every table with a few rows for connection `conn1`,
project `projectKey` and configuration scope `configScope`. Constraints:

- the upgrade test must declare connection `conn1`, otherwise the backend purges its data at startup
- known findings are stored with the OS path separator, so they use a file without folders to be readable on every OS
- the local-only issue is resolved in the year 2100, because resolved local-only issues are purged one week after
  their resolution

When adding a table, extend the generator so that the next snapshot covers it.

## Limitations

The snapshot is written after a clean shutdown. SLCORE-2681 (H2 2.5.252 failing to open files written by 2.4.240) only
happened on files left by an unclean shutdown, so this test would not have caught it. That case is SLCORE-2751.

Only the latest release is covered. Users who skip releases go through older storage stacks that are no longer
tested once a release changes the H2 version, the Flyway version or the migrations.
