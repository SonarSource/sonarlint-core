# SonarLint Core: Build dependencies & logic

This directory contains libraries, tools or scripts that are used by the build, e.g. as
dependencies. These are not mend to be published but will be used / build for every build.

## Maven Shade Plugin: Transformer for Bndtools

This is an extension to the [Maven Shade plug-in](https://maven.apache.org/plugins/maven-shade-plugin/)
in order to work correctly on *MANIFEST.MF* files when used alongside the
[Bndtools](https://github.com/bndtools/bnd/tree/master/maven-plugins/bnd-maven-plugin) on the same
module. It provides a custom
[Resource Transformer](https://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html).

The transformer `org.sonarsource.sonarlint.maven.shade.ext.ManifestBndTransformer` is used in order
to exchange the *MANIFEST.MF* file on normal and sources JAR archives if the configuration offers a
replacement. In the case of `sonarlint-java-client-osgi` this is used as the Bndtools are
generating the *MANIFEST.MF* files and the OSGi content while the Maven Shade plug-in is used to
pack the necessary dependencies or relocate others.
