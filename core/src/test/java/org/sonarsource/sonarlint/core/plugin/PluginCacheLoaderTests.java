/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.ZipUtils;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.plugin.PluginIndex.PluginReference;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginCacheLoaderTests {

  private static final String V1_0 = "1.0";

  private static final String FAKE_PLUGIN_KEY = "pluginkey";

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private PluginCacheLoader underTest;
  private PluginIndex pluginIndex;
  private PluginCache pluginCache;
  private PluginVersionChecker pluginVersionChecker;
  private Set<Language> enabledLanguages;

  @BeforeEach
  public void prepare() {
    pluginIndex = mock(PluginIndex.class);
    pluginCache = mock(PluginCache.class);
    pluginVersionChecker = mock(PluginVersionChecker.class);
    ConnectedGlobalConfiguration configuration = mock(ConnectedGlobalConfiguration.class);
    enabledLanguages = new HashSet<>();
    when(configuration.getEnabledLanguages()).thenReturn(enabledLanguages);
    underTest = new PluginCacheLoader(pluginVersionChecker, pluginCache, pluginIndex, configuration);

    when(pluginVersionChecker.isVersionSupported(any(), ArgumentMatchers.<String>any())).thenThrow(new IllegalStateException("Non specified behavior"));
    when(pluginVersionChecker.isVersionSupported(any(), ArgumentMatchers.<Version>any())).thenThrow(new IllegalStateException("Non specified behavior"));
  }

  @Test
  public void load_no_plugins() {
    underTest.load();

    assertThat(logTester.logs()).contains("Load plugins");
    assertThat(logsWithoutStartStop()).isEmpty();
  }

  @Test
  public void load_plugin_fail_if_missing_storage() {
    when(pluginIndex.references()).thenReturn(singletonList(new PluginIndex.PluginReference("abcd", "sonarjs.jar")));
    when(pluginCache.get("sonarjs.jar", "abcd")).thenReturn(null);

    StorageException thrown = assertThrows(StorageException.class, () -> underTest.load(), "Expected exception");

    assertThat(thrown.getMessage()).contains("Couldn't find plugin 'sonarjs.jar' in the cache. Please update the binding");
  }

  @Test
  public void load_plugin_fail_if_missing_jar(@TempDir Path storage) {
    when(pluginIndex.references()).thenReturn(singletonList(new PluginIndex.PluginReference("abcd", "sonarjs.jar")));
    when(pluginCache.get("sonarjs.jar", "abcd")).thenReturn(storage.resolve("sonarjs.jar"));

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.load(), "Expected exception");

    // Exception is either FileNotFoundException or NoSuchFileException depending on the JRE version
    assertThat(thrown).hasRootCauseInstanceOf(IOException.class);
  }

  @Test
  public void load_plugin_fail_if_corrupted_jar(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar");
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));

    MessageException thrown = assertThrows(MessageException.class, () -> underTest.load(), "Expected exception");

    assertThat(thrown).hasMessageMatching("File is not a plugin. Please delete it and restart: (.*)sonarjs.jar");
  }

  @Test
  public void load_plugin_skip_unsupported_plugins_api_version(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, true, "99.9", null));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(false).when(pluginVersionChecker).isVersionSupported(eq(FAKE_PLUGIN_KEY), eq(Version.create(V1_0)));
    when(pluginVersionChecker.getMinimumVersion(FAKE_PLUGIN_KEY)).thenReturn("2.0");

    assertThat(underTest.load()).hasSize(0);
    assertThat(logsWithoutStartStop()).contains("Code analyzer 'pluginkey' needs plugin API 99.9 while SonarLint supports only up to 8.2. Skip loading it.");
  }

  @Test
  public void load_plugin_skip_unsupported_plugins_version(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, true, null, null));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(false).when(pluginVersionChecker).isVersionSupported(eq(FAKE_PLUGIN_KEY), eq(Version.create(V1_0)));
    when(pluginVersionChecker.getMinimumVersion(FAKE_PLUGIN_KEY)).thenReturn("2.0");

    assertThat(underTest.load()).hasSize(0);
    assertThat(logsWithoutStartStop()).contains("Code analyzer 'pluginkey' version '1.0' is not supported (minimal version is '2.0'). Skip loading it.");
  }

  @Test
  public void load_plugin_skip_unsupported_by_sonarlint(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, null, null, null));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(true).when(pluginVersionChecker).isVersionSupported(eq(FAKE_PLUGIN_KEY), eq(Version.create(V1_0)));

    assertThat(underTest.load()).hasSize(0);
    assertThat(logsWithoutStartStop()).contains("Code analyzer 'pluginkey' is not compatible with SonarLint. Skip loading it.");
  }

  @Test
  public void load_plugin_skip_explicitely_unsupported_by_sonarlint(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, false, null, null));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(true).when(pluginVersionChecker).isVersionSupported(eq(FAKE_PLUGIN_KEY), eq(Version.create(V1_0)));

    assertThat(underTest.load()).hasSize(0);
    assertThat(logsWithoutStartStop()).contains("Code analyzer 'pluginkey' is not compatible with SonarLint. Skip loading it.");
  }

  @Test
  public void load_plugin_skip_not_enabled_languages(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, Language.JS.getPluginKey(), V1_0, true, null, null));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(true).when(pluginVersionChecker).isVersionSupported(eq(Language.JS.getPluginKey()), eq(Version.create(V1_0)));
    enabledLanguages.add(Language.TS);

    assertThat(underTest.load()).hasSize(0);
    assertThat(logsWithoutStartStop()).contains("Code analyzer 'javascript' is excluded in this version of SonarLint. Skip loading it.");
  }

  @Test
  public void load_plugin_skip_plugins_having_excluded_base_plugin(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, true, null, Language.JS.getPluginKey()));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(true).when(pluginVersionChecker).isVersionSupported(eq(FAKE_PLUGIN_KEY), eq(Version.create(V1_0)));
    enabledLanguages.add(Language.TS);

    assertThat(underTest.load()).hasSize(0);
    assertThat(logsWithoutStartStop()).contains("Code analyzer 'pluginkey' is transitively excluded in this version of SonarLint. Skip loading it.");
  }

  @Test
  public void load_plugin_skip_plugins_having_excluded_required_plugin(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar",
      path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, true, null, null, Language.JS.getPluginKey() + ":1.0", "required2:1.0"));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(true).when(pluginVersionChecker).isVersionSupported(eq(FAKE_PLUGIN_KEY), eq(Version.create(V1_0)));
    enabledLanguages.add(Language.TS);

    assertThat(underTest.load()).hasSize(0);
    assertThat(logsWithoutStartStop()).contains("Code analyzer 'pluginkey' is transitively excluded in this version of SonarLint. Skip loading it.");
  }

  // SLCORE-259
  @Test
  public void load_plugin_ignore_dependency_between_sonarjs_and_sonarts(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar",
      path -> createPluginManifest(path, Language.JS.getPluginKey(), V1_0, true, null, null, Language.TS.getPluginKey() + ":1.0"));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(true).when(pluginVersionChecker).isVersionSupported(eq(Language.JS.getPluginKey()), eq(Version.create(V1_0)));
    enabledLanguages.add(Language.JS);

    assertThat(underTest.load()).as(logsWithoutStartStop().collect(Collectors.joining("\n"))).hasSize(1);
  }

  @Test
  public void load_plugin(@TempDir Path storage) throws IOException {
    PluginReference fakePlugin = fakePlugin(storage, "sonarjs.jar", path -> createPluginManifest(path, FAKE_PLUGIN_KEY, V1_0, true, "7.9", "basePluginKey"));
    when(pluginIndex.references()).thenReturn(singletonList(fakePlugin));
    doReturn(true).when(pluginVersionChecker).isVersionSupported(eq(FAKE_PLUGIN_KEY), eq(Version.create(V1_0)));

    assertThat(underTest.load()).as(logsWithoutStartStop().collect(Collectors.joining("\n"))).hasSize(1);
  }

  private void createPluginManifest(Path path, String pluginKey, String version, Boolean sonarlintSupported, @Nullable String sqApiVersion, @Nullable String basePlugin,
    String... requirePlugins) {
    Path manifestPath = path.resolve(JarFile.MANIFEST_NAME);
    try {
      Files.createDirectories(manifestPath.getParent());
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      manifest.getMainAttributes().putValue(PluginManifest.KEY_ATTRIBUTE, pluginKey);
      manifest.getMainAttributes().putValue(PluginManifest.VERSION_ATTRIBUTE, version);
      if (sonarlintSupported != null) {
        manifest.getMainAttributes().putValue(PluginManifest.SONARLINT_SUPPORTED, String.valueOf(sonarlintSupported));
      }
      if (sqApiVersion != null) {
        manifest.getMainAttributes().putValue(PluginManifest.SONAR_VERSION_ATTRIBUTE, sqApiVersion);
      }
      if (basePlugin != null) {
        manifest.getMainAttributes().putValue(PluginManifest.BASE_PLUGIN, basePlugin);
      }
      if (requirePlugins.length > 0) {
        manifest.getMainAttributes().putValue(PluginManifest.REQUIRE_PLUGINS_ATTRIBUTE, Stream.of(requirePlugins).collect(joining(",")));
      }
      try (OutputStream fos = Files.newOutputStream(manifestPath, StandardOpenOption.CREATE_NEW)) {
        manifest.write(fos);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private PluginIndex.PluginReference fakePlugin(Path storage, String filename, Consumer<Path>... populators) throws IOException {
    Path pluginJar = storage.resolve(filename);
    Path pluginTmpDir = Files.createTempDirectory(storage, "plugin");
    Stream.of(populators).forEach(p -> p.accept(pluginTmpDir));
    ZipUtils.zipDir(pluginTmpDir.toFile(), pluginJar.toFile());
    String hash = "hash" + filename;
    when(pluginCache.get(filename, hash)).thenReturn(pluginJar);
    return new PluginIndex.PluginReference(hash, filename);
  }

  private Stream<String> logsWithoutStartStop() {
    return logTester.logs().stream()
      .filter(s -> !s.equals("Load plugins"))
      .filter(s -> !s.matches("Load plugins \\(done\\) \\| time=(.*)ms"));
  }

}
