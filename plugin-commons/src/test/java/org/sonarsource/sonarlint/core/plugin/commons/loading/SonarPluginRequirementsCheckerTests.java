/*
 * SonarLint Core - Plugin Commons
 * Copyright (C) 2016-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.plugin.commons.loading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.ZipUtils;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.PluginsMinVersions;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.plugin.commons.loading.SonarPluginRequirementsChecker.isCompatibleWith;

class SonarPluginRequirementsCheckerTests {

  private static final Set<Language> NONE = Set.of();

  private static final String V1_0 = "1.0";

  private static final String FAKE_PLUGIN_KEY = "pluginkey";
  private static final org.sonar.api.utils.Version FAKE_PLUGIN_API_VERSION = org.sonar.api.utils.Version.parse("8.1.2");

  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester();

  private SonarPluginRequirementsChecker underTest;
  private PluginsMinVersions pluginMinVersions;

  @BeforeEach
  void prepare() {
    pluginMinVersions = spy(new PluginsMinVersions());
    doReturn(V1_0).when(pluginMinVersions).getMinimumVersion(FAKE_PLUGIN_KEY);

    underTest = new SonarPluginRequirementsChecker(pluginMinVersions, FAKE_PLUGIN_API_VERSION);
  }

  @Test
  void load_no_plugins() {
    var loadedPlugins = underTest.checkRequirements(Set.of(), NONE, null, true, null);

    assertThat(loadedPlugins).isEmpty();
  }

  @Test
  void load_plugin_fail_if_missing_jar() {
    Set<Path> jars = Set.of(Paths.get("doesntexists.jar"));
    var checkRequirements = underTest.checkRequirements(jars, NONE, null, true, null);

    assertThat(checkRequirements).isEmpty();
    assertThat(logTester.logs(Level.ERROR)).contains("Unable to load plugin doesntexists.jar");
  }

  @Test
  void load_plugin_skip_corrupted_jar(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "sonarjs.jar");
    Set<Path> jars = Set.of(fakePlugin);

    var checkRequirements = underTest.checkRequirements(jars, NONE, null, true, null);

    assertThat(checkRequirements).isEmpty();
    assertThat(logTester.logs(Level.ERROR)).contains("Unable to load plugin " + fakePlugin);
  }

  @Test
  void load_plugin_skip_unsupported_plugins_api_version(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0,
      withSqApiVersion("99.9")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, null);

    assertThat(loadedPlugins.values())
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, SkipReason.IncompatiblePluginApi.INSTANCE));
    assertThat(logsWithoutStartStop())
      .contains("Plugin 'pluginkey' requires plugin API 99.9 while SonarLint supports only up to " + FAKE_PLUGIN_API_VERSION + ". Skip loading it.");
  }

  @Test
  void load_plugin_skip_unsupported_plugins_version(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0));
    Set<Path> jars = Set.of(fakePlugin);

    when(pluginMinVersions.getMinimumVersion(FAKE_PLUGIN_KEY)).thenReturn("2.0");

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, null);

    assertThat(loadedPlugins.values())
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.IncompatiblePluginVersion("2.0")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' version '1.0' is not supported (minimal version is '2.0'). Skip loading it.");
  }

  @Test
  void load_plugin_skip_not_enabled_languages(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "sonarphp.jar", path -> createPluginManifest(path, Language.PHP.getPluginKey(), V1_0));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, Set.of(Language.TS), null, true, null);

    assertThat(loadedPlugins.values()).extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("php", true, new SkipReason.LanguagesNotEnabled(new HashSet<>(asList(Language.PHP)))));
    assertThat(logsWithoutStartStop()).contains("Plugin 'php' is excluded because language 'PHP' is not enabled. Skip loading it.");
  }

  @Test
  void load_plugin_skip_not_enabled_languages_multiple(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, Language.C.getPluginKey(), V1_0));
    Set<Path> jars = Set.of(fakePlugin);
    doReturn(V1_0).when(pluginMinVersions).getMinimumVersion(Language.C.getPluginKey());

    var loadedPlugins = underTest.checkRequirements(jars, Set.of(Language.JS), null, true, null);

    assertThat(loadedPlugins.values()).extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("cpp", true, new SkipReason.LanguagesNotEnabled(new HashSet<>(asList(Language.C, Language.CPP, Language.OBJC)))));
    assertThat(logsWithoutStartStop()).contains("Plugin 'cpp' is excluded because none of languages 'C,C++,Objective-C' are enabled. Skip loading it.");
  }

  @Test
  void load_plugin_load_even_if_only_one_language_enabled(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, Language.C.getPluginKey(), V1_0));
    Set<Path> jars = Set.of(fakePlugin);
    doReturn(V1_0).when(pluginMinVersions).getMinimumVersion(Language.CPP.getPluginKey());

    var loadedPlugins = underTest.checkRequirements(jars, Set.of(Language.CPP), null, true, null);

    assertThat(loadedPlugins.values()).extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("cpp", false, null));
  }

  @Test
  void load_plugin_skip_plugins_having_missing_base_plugin(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withBasePlugin(Language.JS.getPluginKey())));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, null);

    assertThat(loadedPlugins.values()).extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedDependency("javascript")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' dependency on 'javascript' is unsatisfied. Skip loading it.");
  }

  @Test
  void load_plugin_skip_plugins_having_skipped_base_plugin(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withBasePlugin(Language.JS.getPluginKey())));
    var fakeBasePlugin = fakePlugin(storage, "base.jar",
      path -> createPluginManifest(path, Language.JS.getPluginKey(), V1_0));
    Set<Path> jars = Set.of(fakePlugin, fakeBasePlugin);

    // Ensure base plugin is skipped because of min version
    doReturn("2.0").when(pluginMinVersions).getMinimumVersion(Language.JS.getPluginKey());

    var loadedPlugins = underTest.checkRequirements(jars, Set.of(Language.JS), null, true, null);

    assertThat(loadedPlugins.values()).extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(
        tuple("pluginkey", true, new SkipReason.UnsatisfiedDependency("javascript")),
        tuple("javascript", true, new SkipReason.IncompatiblePluginVersion("2.0")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' dependency on 'javascript' is unsatisfied. Skip loading it.");
  }

  @Test
  void load_plugin_having_base_plugin(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withBasePlugin(Language.JS.getPluginKey())));
    var fakeBasePlugin = fakePlugin(storage, "base.jar",
      path -> createPluginManifest(path, Language.JS.getPluginKey(), V1_0));
    Set<Path> jars = Set.of(fakePlugin, fakeBasePlugin);

    // Ensure base plugin is not skipped
    doReturn(V1_0).when(pluginMinVersions).getMinimumVersion(Language.JS.getPluginKey());

    var loadedPlugins = underTest.checkRequirements(jars, Set.of(Language.JS), null, true, null);

    assertThat(loadedPlugins.values())
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(
        tuple("pluginkey", false, null),
        tuple("javascript", false, null));
  }

  @Test
  void load_plugin_skip_plugins_having_missing_required_plugin(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withRequiredPlugins("required2:1.0")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, null);

    assertThat(loadedPlugins.values())
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedDependency("required2")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' dependency on 'required2' is unsatisfied. Skip loading it.");
  }

  @Test
  void load_plugin_ignore_license_plugin_dependency(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withRequiredPlugins("license:1.0")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, null);

    assertThat(loadedPlugins.values())
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped)
      .containsOnly(tuple("pluginkey", false));
    assertThat(logsWithoutStartStop()).isEmpty();
  }

  @Test
  void load_plugin_skip_plugins_having_skipped_required_plugin(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withRequiredPlugins("required2:1.0")));
    var fakeDepPlugin = fakePlugin(storage, "dep.jar", path -> createPluginManifest(path, "required2", V1_0));
    Set<Path> jars = Set.of(fakePlugin, fakeDepPlugin);

    // Ensure dep plugin is skipped because of min version
    doReturn("2.0").when(pluginMinVersions).getMinimumVersion("required2");

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, null);

    assertThat(loadedPlugins.values()).extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedDependency("required2")),
        tuple("required2", true, new SkipReason.IncompatiblePluginVersion("2.0")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' dependency on 'required2' is unsatisfied. Skip loading it.");
  }

  // SLCORE-259
  @Test
  void load_plugin_ignore_dependency_between_sonarjs_and_sonarts(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "sonarjs.jar",
      path -> createPluginManifest(path, Language.JS.getPluginKey(), V1_0, withRequiredPlugins("typescript:1.0")));
    Set<Path> jars = Set.of(fakePlugin);
    doReturn(true).when(pluginMinVersions).isVersionSupported(Language.JS.getPluginKey(), Version.create(V1_0));

    var loadedPlugins = underTest.checkRequirements(jars, Set.of(Language.JS), null, true, null);

    assertThat(loadedPlugins.values()).as(logsWithoutStartStop().collect(Collectors.joining("\n")))
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped)
      .containsOnly(tuple(Language.JS.getPluginKey(), false));
  }

  @Test
  void load_plugin(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSqApiVersion("7.9")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, null);

    assertThat(loadedPlugins.values()).as(logsWithoutStartStop().collect(Collectors.joining("\n")))
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped)
      .containsOnly(tuple(FAKE_PLUGIN_KEY, false));
  }

  @Test
  void load_plugin_skip_plugins_having_unsatisfied_jre(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withJreMinVersion("11")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, Version.create("1.8"), true, null);

    assertThat(loadedPlugins.values()).extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.8", "11")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' requires JRE 11 while current is 1.8. Skip loading it.");
  }

  @Test
  void load_plugin_having_satisfied_nodejs(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withNodejsMinVersion("10.1.2")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, Optional.of(Version.create("10.1.3")));

    assertThat(loadedPlugins.values())
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped)
      .containsOnly(tuple("pluginkey", false));
  }

  @Test
  void load_plugin_having_satisfied_nodejs_nightly(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withNodejsMinVersion("15.0.0")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, Optional.of(Version.create("15.0.0-nightly20200921039c274dde")));

    assertThat(loadedPlugins.values())
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped)
      .containsOnly(tuple("pluginkey", false));
  }

  @Test
  void load_plugin_skip_plugins_having_unsatisfied_nodejs_version(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withNodejsMinVersion("10.1.2")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, Optional.of(Version.create("10.1.1")));

    assertThat(loadedPlugins.values())
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, "10.1.1", "10.1.2")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' requires Node.js 10.1.2 while current is 10.1.1. Skip loading it.");
  }

  @Test
  void load_plugin_skip_plugins_having_unsatisfied_nodejs(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withNodejsMinVersion("10.1.2")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, null, true, Optional.empty());

    assertThat(loadedPlugins.values()).extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, null, "10.1.2")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' requires Node.js 10.1.2. Skip loading it.");
  }

  @Test
  void load_plugin_having_satisfied_jre(@TempDir Path storage) throws IOException {
    var fakePlugin = fakePlugin(storage, "fake.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withJreMinVersion("1.7")));
    Set<Path> jars = Set.of(fakePlugin);

    var loadedPlugins = underTest.checkRequirements(jars, NONE, Version.create("1.8"), true, Optional.empty());

    assertThat(loadedPlugins.values())
      .extracting(r -> r.getPlugin().getKey(), PluginRequirementsCheckResult::isSkipped)
      .containsOnly(tuple("pluginkey", false));
  }

  @Test
  void test_isCompatibleWith() throws IOException {
    assertThat(isCompatibleWith(withMinSqVersion("1.1"), Version.create("1.1"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("1.1"), Version.create("1.1.0"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("1.0"), Version.create("1.0.0"))).isTrue();

    assertThat(isCompatibleWith(withMinSqVersion("1.0"), Version.create("1.1"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("1.1.1"), Version.create("1.1.2"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("2.0"), Version.create("2.1.0"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("3.2"), Version.create("3.2-RC1"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("3.2"), Version.create("3.2-RC2"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("3.2"), Version.create("3.1-RC2"))).isFalse();

    assertThat(isCompatibleWith(withMinSqVersion("1.1"), Version.create("1.0"))).isFalse();
    assertThat(isCompatibleWith(withMinSqVersion("2.0.1"), Version.create("2.0.0"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("2.10"), Version.create("2.1"))).isFalse();
    assertThat(isCompatibleWith(withMinSqVersion("10.10"), Version.create("2.2"))).isFalse();

    assertThat(isCompatibleWith(withMinSqVersion("1.1-SNAPSHOT"), Version.create("1.0"))).isFalse();
    assertThat(isCompatibleWith(withMinSqVersion("1.1-SNAPSHOT"), Version.create("1.1"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("1.1-SNAPSHOT"), Version.create("1.2"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("1.0.1-SNAPSHOT"), Version.create("1.0"))).isTrue();

    assertThat(isCompatibleWith(withMinSqVersion("3.1-RC2"), Version.create("3.2-SNAPSHOT"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("3.1-RC1"), Version.create("3.2-RC2"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion("3.1-RC1"), Version.create("3.1-RC2"))).isTrue();

    assertThat(isCompatibleWith(withMinSqVersion(null), Version.create("0"))).isTrue();
    assertThat(isCompatibleWith(withMinSqVersion(null), Version.create("3.1"))).isTrue();

    assertThat(isCompatibleWith(withMinSqVersion("7.0.0.12345"), Version.create("7.0"))).isTrue();
  }

  PluginInfo withMinSqVersion(@Nullable String version) {
    var plugin = mock(PluginInfo.class);
    when(plugin.getKey()).thenReturn("foo");
    when(plugin.getMinimalSqVersion()).thenReturn(Optional.ofNullable(version).map(Version::create).orElse(null));
    return plugin;
  }

  private void createPluginManifest(Path path, String pluginKey, String version, Consumer<Attributes>... manifestAttributesPopulators) {
    var manifestPath = path.resolve(JarFile.MANIFEST_NAME);
    try {
      Files.createDirectories(manifestPath.getParent());
      var manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      manifest.getMainAttributes().putValue(SonarPluginManifest.KEY_ATTRIBUTE, pluginKey);
      manifest.getMainAttributes().putValue(SonarPluginManifest.VERSION_ATTRIBUTE, version);
      Stream.of(manifestAttributesPopulators).forEach(p -> p.accept(manifest.getMainAttributes()));
      try (var fos = Files.newOutputStream(manifestPath, StandardOpenOption.CREATE_NEW)) {
        manifest.write(fos);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Consumer<Attributes> withSqApiVersion(String sqApiVersion) {
    return a -> a.putValue(SonarPluginManifest.SONAR_VERSION_ATTRIBUTE, sqApiVersion);
  }

  private Consumer<Attributes> withJreMinVersion(String jreMinVersion) {
    return a -> a.putValue(SonarPluginManifest.JRE_MIN_VERSION, jreMinVersion);
  }

  private Consumer<Attributes> withNodejsMinVersion(String nodeMinVersion) {
    return a -> a.putValue(SonarPluginManifest.NODEJS_MIN_VERSION, nodeMinVersion);
  }

  private Consumer<Attributes> withBasePlugin(String basePlugin) {
    return a -> a.putValue(SonarPluginManifest.BASE_PLUGIN, basePlugin);
  }

  private Consumer<Attributes> withRequiredPlugins(String... requirePlugins) {
    return a -> a.putValue(SonarPluginManifest.REQUIRE_PLUGINS_ATTRIBUTE, Stream.of(requirePlugins).collect(joining(",")));
  }

  private Path fakePlugin(Path storage, String filename, Consumer<Path>... populators) throws IOException {
    var pluginJar = storage.resolve(filename);
    var pluginTmpDir = Files.createTempDirectory(storage, "plugin");
    Stream.of(populators).forEach(p -> p.accept(pluginTmpDir));
    ZipUtils.zipDir(pluginTmpDir.toFile(), pluginJar.toFile());
    return pluginJar;
  }

  private Stream<String> logsWithoutStartStop() {
    return logTester.logs().stream()
      .filter(s -> !s.equals("Load plugins"))
      .filter(s -> !s.matches("Load plugins \\(done\\) \\| time=(.*)ms"));
  }

}
