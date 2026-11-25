# Agent instructions

## Java packages

When you create a new Java package — a new directory under a module's `src/main/java` that contains `.java` files — add a `package-info.java` in that directory in the same change.

Sonar rule `java:S1228` requires this file. New violations fail the quality gate on master.

Add one file per package directory, including nested packages.

Copy the shape of an existing `package-info.java` in the same Maven module:

- Keep the license header, including the module name on the second line (for example `SonarLint Core - Implementation` in `backend/core`).
- Annotate the package with `@ParametersAreNonnullByDefault`.
- Import `javax.annotation.ParametersAreNonnullByDefault`.
- Declare the new package.

This applies to production sources (`src/main/java`).
