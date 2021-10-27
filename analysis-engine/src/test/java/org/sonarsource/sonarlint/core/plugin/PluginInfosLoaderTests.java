/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.ZipUtils;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonarsource.sonarlint.core.GlobalAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement;
import org.sonarsource.sonarlint.core.client.api.common.Version;
import org.sonarsource.sonarlint.core.plugin.PluginIndex.PluginReference;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class PluginInfosLoaderTests {

  private static final String V1_0 = "1.0";

  private static final String FAKE_PLUGIN_KEY = "pluginkey";

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private PluginInfosLoader underTest;
  private PluginIndex pluginIndex;
  private PluginCache pluginCache;
  private PluginVersionChecker pluginVersionChecker;
  private Set<Language> enabledLanguages;
  private System2 system2;
  private GlobalAnalysisConfiguration globalConfig;

  @BeforeEach
  public void prepare() {
    pluginIndex = mock(PluginIndex.class);
    pluginCache = mock(PluginCache.class);
    system2 = mock(System2.class);
    pluginVersionChecker = spy(new PluginVersionChecker());
    globalConfig = mock(GlobalAnalysisConfiguration.class);
    enabledLanguages = new HashSet<>();
    when(globalConfig.getEnabledLanguages()).thenReturn(enabledLanguages);
    underTest = new PluginInfosLoader(pluginVersionChecker, pluginCache, pluginIndex, globalConfig, system2);

    when(system2.property("java.specification.version")).thenReturn(System.getProperty("java.specification.version"));
    doReturn(V1_0).when(pluginVersionChecker).getMinimumVersion(FAKE_PLUGIN_KEY);
  }

  @Test
  void load_no_plugins() {
    underTest.load();

    assertThat(logTester.logs()).contains("Load plugins");
    assertThat(logsWithoutStartStop()).isEmpty();
  }

  @Test
  void load_plugin_fail_if_missing_storage() {
    when(pluginIndex.references()).thenReturn(singletonList(new PluginIndex.PluginReference("abcd", "sonarjs.jar", false)));
    when(pluginCache.get("sonarjs.jar", "abcd")).thenReturn(null);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.load(), "Expected exception");

    assertThat(thrown.getMessage()).contains("Couldn't find plugin 'sonarjs.jar' in the cache. Please update the binding");
  }

  @Test
  void load_plugin_fail_if_missing_jar(@TempDir Path storage) {
    when(pluginIndex.references()).thenReturn(singletonList(new PluginIndex.PluginReference("abcd", "sonarjs.jar", false)));
    when(pluginCache.get("sonarjs.jar", "abcd")).thenReturn(storage.resolve("sonarjs.jar"));

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.load(), "Expected exception");

    // Exception is either FileNotFoundException or NoSuchFileException depending on the JRE version
    assertThat(thrown).hasRootCauseInstanceOf(IOException.class);
  }

  @Test
  void load_plugin_fail_if_corrupted_jar(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar");
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    MessageException thrown = assertThrows(MessageException.class, () -> underTest.load(), "Expected exception");

    assertThat(thrown).hasMessageMatching("File is not a plugin. Please delete it and restart: (.*)sonarjs.jar");
  }

  @Test
  void load_plugin_skip_unsupported_plugins_api_version(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0,
      withSonarLintSupported(true), withSqApiVersion("99.9")));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, SkipReason.IncompatiblePluginApi.INSTANCE));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' requires plugin API 99.9 while SonarLint supports only up to 8.9. Skip loading it.");
  }

  @Test
  void load_plugin_skip_unsupported_plugins_version(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true)));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    when(pluginVersionChecker.getMinimumVersion(FAKE_PLUGIN_KEY)).thenReturn("2.0");

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.IncompatiblePluginVersion("2.0")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' version '1.0' is not supported (minimal version is '2.0'). Skip loading it.");
  }

  @Test
  void load_plugin_skip_unsupported_by_sonarlint(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    assertThat(underTest.load()).hasSize(0);
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' is not compatible with SonarLint. Skip loading it.");
  }

  @Test
  void load_plugin_skip_explicitely_unsupported_by_sonarlint(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(false)));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    assertThat(underTest.load()).hasSize(0);
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' is not compatible with SonarLint. Skip loading it.");
  }

  @Test
  void load_plugin_skip_not_enabled_languages(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarphp.jar", path -> createPluginManifest(path, Language.PHP.getPluginKey(), V1_0, withSonarLintSupported(true)));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    enabledLanguages.add(Language.TS);

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("php", true, new SkipReason.LanguagesNotEnabled(new HashSet<>(asList(Language.PHP)))));
    assertThat(logsWithoutStartStop()).contains("Plugin 'php' is excluded because language 'PHP' is not enabled. Skip loading it.");
  }

  @Test
  void load_plugin_skip_not_enabled_languages_multiple(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, Language.C.getPluginKey(), V1_0, withSonarLintSupported(true)));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(V1_0).when(pluginVersionChecker).getMinimumVersion(eq(Language.C.getPluginKey()));

    enabledLanguages.add(Language.JS);

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("cpp", true, new SkipReason.LanguagesNotEnabled(new HashSet<>(asList(Language.C, Language.CPP, Language.OBJC)))));
    assertThat(logsWithoutStartStop()).contains("Plugin 'cpp' is excluded because none of languages 'C,C++,Objective-C' are enabled. Skip loading it.");
  }

  @Test
  void load_plugin_load_even_if_only_one_language_enabled(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, Language.C.getPluginKey(), V1_0, withSonarLintSupported(true)));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(V1_0).when(pluginVersionChecker).getMinimumVersion(eq(Language.C.getPluginKey()));

    enabledLanguages.add(Language.CPP);

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("cpp", false, null));
  }

  @Test
  void load_plugin_skip_plugins_having_missing_base_plugin(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withBasePlugin(Language.JS.getPluginKey())));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedDependency("javascript")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' dependency on 'javascript' is unsatisfied. Skip loading it.");
  }

  @Test
  void load_plugin_skip_plugins_having_skipped_base_plugin(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withBasePlugin(Language.JS.getPluginKey())));
    PluginReference fakeBasePlugin = fakePlugin(storage, "base.jar",
      path -> createPluginManifest(path, Language.JS.getPluginKey(), V1_0, withSonarLintSupported(true)));
    when(pluginIndex.references()).thenReturn(asList(fakePlugin, fakeBasePlugin));

    // Ensure base plugin is skipped because of min version
    doReturn("2.0").when(pluginVersionChecker).getMinimumVersion(eq(Language.JS.getPluginKey()));
    enabledLanguages.add(Language.JS);

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(
        tuple("pluginkey", true, new SkipReason.UnsatisfiedDependency("javascript")),
        tuple("javascript", true, new SkipReason.IncompatiblePluginVersion("2.0")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' dependency on 'javascript' is unsatisfied. Skip loading it.");
  }

  @Test
  void load_plugin_having_base_plugin(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withBasePlugin(Language.JS.getPluginKey())));
    PluginReference fakeBasePlugin = fakePlugin(storage, "base.jar",
      path -> createPluginManifest(path, Language.JS.getPluginKey(), V1_0, withSonarLintSupported(true)));
    when(pluginIndex.references()).thenReturn(asList(fakePlugin, fakeBasePlugin));

    // Ensure base plugin is not skipped
    doReturn(V1_0).when(pluginVersionChecker).getMinimumVersion(eq(Language.JS.getPluginKey()));
    enabledLanguages.add(Language.JS);

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(
        tuple("pluginkey", false, null),
        tuple("javascript", false, null));
  }

  @Test
  void load_plugin_skip_plugins_having_missing_required_plugin(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withRequiredPlugins("required2:1.0")));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedDependency("required2")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' dependency on 'required2' is unsatisfied. Skip loading it.");
  }

  @Test
  void load_plugin_ignore_license_plugin_dependency(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withRequiredPlugins("license:1.0")));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", false, null));
    assertThat(logsWithoutStartStop()).isEmpty();
  }

  @Test
  void load_plugin_skip_plugins_having_skipped_required_plugin(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withRequiredPlugins("required2:1.0")));
    PluginReference fakeDepPlugin = fakePlugin(storage, "dep.jar", path -> createPluginManifest(path, "required2", V1_0, withSonarLintSupported(true)));
    when(pluginIndex.references()).thenReturn(asList(fakePlugin, fakeDepPlugin));

    // Ensure dep plugin is skipped because of min version
    doReturn("2.0").when(pluginVersionChecker).getMinimumVersion("required2");

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedDependency("required2")),
        tuple("required2", true, new SkipReason.IncompatiblePluginVersion("2.0")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' dependency on 'required2' is unsatisfied. Skip loading it.");
  }

  // SLCORE-259
  @Test
  void load_plugin_ignore_dependency_between_sonarjs_and_sonarts(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar",
      path -> createPluginManifest(path, Language.JS.getPluginKey(), V1_0, withSonarLintSupported(true), withRequiredPlugins("typescript:1.0")));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(true).when(pluginVersionChecker).isVersionSupported(eq(Language.JS.getPluginKey()), eq(Version.create(V1_0)));
    enabledLanguages.add(Language.JS);

    assertThat(underTest.load().values()).as(logsWithoutStartStop().collect(Collectors.joining("\n")))
      .extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple(Language.JS.getPluginKey(), false, null));
  }

  @Test
  void load_plugin(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withSqApiVersion("7.9")));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    assertThat(underTest.load().values()).as(logsWithoutStartStop().collect(Collectors.joining("\n")))
      .extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple(FAKE_PLUGIN_KEY, false, null));
  }

  @Test
  void load_plugin_skip_plugins_having_unsatisfied_jre(@TempDir Path storage) throws IOException {
    when(system2.property("java.specification.version")).thenReturn("1.8");

    PluginReference fakePlugin = fakePlugin(storage, "fake.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withJreMinVersion("11")));
    when(pluginIndex.references()).thenReturn(asList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.8", "11")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' requires JRE 11 while current is 1.8. Skip loading it.");
  }

  @Test
  void load_plugin_having_satisfied_nodejs(@TempDir Path storage) throws IOException {
    when(globalConfig.getNodeJsVersion()).thenReturn(Version.create("10.1.3"));

    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withNodejsMinVersion("10.1.2")));
    when(pluginIndex.references()).thenReturn(asList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", false, null));
  }

  @Test
  void load_plugin_having_satisfied_nodejs_nightly(@TempDir Path storage) throws IOException {
    when(globalConfig.getNodeJsVersion()).thenReturn(Version.create("15.0.0-nightly20200921039c274dde"));

    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withNodejsMinVersion("15.0.0")));
    when(pluginIndex.references()).thenReturn(asList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", false, null));
  }

  @Test
  void load_plugin_skip_plugins_having_unsatisfied_nodejs_version(@TempDir Path storage) throws IOException {
    when(globalConfig.getNodeJsVersion()).thenReturn(Version.create("10.1.1"));

    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withNodejsMinVersion("10.1.2")));
    when(pluginIndex.references()).thenReturn(asList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, "10.1.1", "10.1.2")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' requires Node.js 10.1.2 while current is 10.1.1. Skip loading it.");
  }

  @Test
  void load_plugin_skip_plugins_having_unsatisfied_nodejs(@TempDir Path storage) throws IOException {
    when(globalConfig.getNodeJsVersion()).thenReturn(null);

    PluginReference fakePlugin = fakePlugin(storage, "fake.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withNodejsMinVersion("10.1.2")));
    when(pluginIndex.references()).thenReturn(asList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", true, new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, null, "10.1.2")));
    assertThat(logsWithoutStartStop()).contains("Plugin 'pluginkey' requires Node.js 10.1.2. Skip loading it.");
  }

  @Test
  void load_plugin_having_satisfied_jre(@TempDir Path storage) throws IOException {
    when(system2.property("java.specification.version")).thenReturn("1.8");

    PluginReference fakePlugin = fakePlugin(storage, "fake.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, withSonarLintSupported(true), withJreMinVersion("1.7")));
    when(pluginIndex.references()).thenReturn(asList(fakePlugin));

    assertThat(underTest.load().values()).extracting(PluginInfo::getName, PluginInfo::isSkipped, p -> p.getSkipReason().orElse(null))
      .containsOnly(tuple("pluginkey", false, null));
  }

  private void createPluginManifest(Path path, String pluginKey, String version, Consumer<Attributes>... manifestAttributesPopulators) {
    Path manifestPath = path.resolve(JarFile.MANIFEST_NAME);
    try {
      Files.createDirectories(manifestPath.getParent());
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      manifest.getMainAttributes().putValue(PluginManifest.KEY_ATTRIBUTE, pluginKey);
      manifest.getMainAttributes().putValue(PluginManifest.VERSION_ATTRIBUTE, version);
      Stream.of(manifestAttributesPopulators).forEach(p -> p.accept(manifest.getMainAttributes()));
      try (OutputStream fos = Files.newOutputStream(manifestPath, StandardOpenOption.CREATE_NEW)) {
        manifest.write(fos);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Consumer<Attributes> withSonarLintSupported(boolean value) {
    return a -> a.putValue(PluginManifest.SONARLINT_SUPPORTED, String.valueOf(value));
  }

  private Consumer<Attributes> withSqApiVersion(String sqApiVersion) {
    return a -> a.putValue(PluginManifest.SONAR_VERSION_ATTRIBUTE, sqApiVersion);
  }

  private Consumer<Attributes> withJreMinVersion(String jreMinVersion) {
    return a -> a.putValue(PluginManifest.JRE_MIN_VERSION, jreMinVersion);
  }

  private Consumer<Attributes> withNodejsMinVersion(String nodeMinVersion) {
    return a -> a.putValue(PluginManifest.NODEJS_MIN_VERSION, nodeMinVersion);
  }

  private Consumer<Attributes> withBasePlugin(String basePlugin) {
    return a -> a.putValue(PluginManifest.BASE_PLUGIN, basePlugin);
  }

  private Consumer<Attributes> withRequiredPlugins(String... requirePlugins) {
    return a -> a.putValue(PluginManifest.REQUIRE_PLUGINS_ATTRIBUTE, Stream.of(requirePlugins).collect(joining(",")));
  }

  private PluginIndex.PluginReference fakePlugin(Path storage, String filename, Consumer<Path>... populators) throws IOException {
    Path pluginJar = storage.resolve(filename);
    Path pluginTmpDir = Files.createTempDirectory(storage, "plugin");
    Stream.of(populators).forEach(p -> p.accept(pluginTmpDir));
    ZipUtils.zipDir(pluginTmpDir.toFile(), pluginJar.toFile());
    String hash = "hash" + filename;
    when(pluginCache.get(filename, hash)).thenReturn(pluginJar);
    return new PluginIndex.PluginReference(hash, filename, false);
  }

  private Stream<String> logsWithoutStartStop() {
    return logTester.logs().stream()
      .filter(s -> !s.equals("Load plugins"))
      .filter(s -> !s.matches("Load plugins \\(done\\) \\| time=(.*)ms"));
  }

}
