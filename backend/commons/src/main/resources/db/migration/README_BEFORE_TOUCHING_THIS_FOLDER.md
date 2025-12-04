Flyway computes checksums of migration files and saves them in the DB.

Once a migration has been shipped and applied by some users, it is forbidden to modify a migration file, as the checksum would differ.

This would fail the migration validation at startup, preventing SQ-IDE from starting.

As a rule of thumb, please do not modify a migration file after it has been merged to master, because we cannot know if it has been applied by users.

Instead, please add a new migration file, by respecting the pattern VX__description.sql, and the numbering (+1 compared to the latest migration).