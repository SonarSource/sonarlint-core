/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.Plugin;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.Version;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MapSettings;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainer;
import org.sonarsource.sonarlint.core.plugin.commons.sonarapi.SonarLintRuntimeImpl;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalysisExtensionInstallerTests {

  private static final String JAVASCRIPT_PLUGIN_KEY = "javascript";
  private static final String FAKE_PLUGIN_KEY = "foo";
  private static final String JAVA_PLUGIN_KEY = "java";

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private static final Configuration EMPTY_CONFIG = new MapSettings(Map.of()).asConfig();
  private static final Version PLUGIN_API_VERSION = Version.create(5, 4, 0);
  private static final long FAKE_PID = 123L;
  private static final SonarLintRuntime RUNTIME = new SonarLintRuntimeImpl(Version.create(8, 0), PLUGIN_API_VERSION, FAKE_PID);
  private AnalysisExtensionInstaller underTest;
  private LoadedPlugins loadedPlugins;
  private SpringComponentContainer container;

  @BeforeEach
  void prepare() {
    loadedPlugins = mock(LoadedPlugins.class);
    container = mock(SpringComponentContainer.class);
    underTest = new AnalysisExtensionInstaller(RUNTIME, loadedPlugins, EMPTY_CONFIG, AnalysisEngineConfiguration.builder().build());
  }

  @Test
  void install_sonarlintside_extensions_with_default_lifespan_in_analysis_container_for_compatible_plugins() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new FakePlugin()));

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(FAKE_PLUGIN_KEY, FakeSonarLintDefaultLifespanComponent.class);
  }

  @Test
  void install_sonarlintside_extensions_with_single_analysis_lifespan_in_analysis_container_for_compatible_plugins() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new FakePlugin(FakeSonarLintSingleAnalysisLifespanComponent.class)));

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(FAKE_PLUGIN_KEY, FakeSonarLintSingleAnalysisLifespanComponent.class);
  }

  @Test
  void install_sonarlintside_extensions_with_multiple_analysis_lifespan_in_global_container_for_compatible_plugins() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new FakePlugin(FakeSonarLintMultipleAnalysisLifespanComponent.class)));

    underTest.install(container, ContainerLifespan.INSTANCE);

    verify(container).addExtension(FAKE_PLUGIN_KEY, FakeSonarLintMultipleAnalysisLifespanComponent.class);
  }

  @Test
  void install_sonarlintside_extensions_with_instance_lifespan_in_global_container_for_compatible_plugins() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new FakePlugin(FakeSonarLintInstanceLifespanComponent.class)));

    underTest.install(container, ContainerLifespan.INSTANCE);

    verify(container).addExtension(FAKE_PLUGIN_KEY, FakeSonarLintInstanceLifespanComponent.class);
  }

  @Test
  void dont_install_sonarlintside_extensions_with_multiple_analysis_lifespan_in_analysis_container_for_compatible_plugins() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new FakePlugin(FakeSonarLintMultipleAnalysisLifespanComponent.class)));

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verifyNoInteractions(container);
  }

  @Test
  void dont_install_sonarlintside_extensions_with_single_analysis_lifespan_in_global_container_for_compatible_plugins() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new FakePlugin(FakeSonarLintSingleAnalysisLifespanComponent.class)));

    underTest.install(container, ContainerLifespan.INSTANCE);

    verifyNoInteractions(container);
  }

  @Test
  void install_sonarlintside_extensions_with_module_lifespan_in_module_container_for_compatible_plugins() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new FakePlugin(FakeSonarLintModuleLifespanComponent.class)));

    underTest.install(container, ContainerLifespan.MODULE);

    verify(container).addExtension(FAKE_PLUGIN_KEY, FakeSonarLintModuleLifespanComponent.class);
  }

  @Test
  void install_sensors_for_sonarsource_plugins() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(JAVA_PLUGIN_KEY, new FakePlugin()));

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(JAVA_PLUGIN_KEY, FakeSensor.class);
  }

  @Test
  void dont_install_sensors_for_non_sonarsource_plugins() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new FakePlugin()));

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container, never()).addExtension(FAKE_PLUGIN_KEY, FakeSensor.class);
  }

  @Test
  void install_typescript_sensor_if_typescript_language_enabled_in_connected_mode() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(JAVASCRIPT_PLUGIN_KEY, new FakePlugin()));

    underTest = new AnalysisExtensionInstaller(RUNTIME, loadedPlugins, EMPTY_CONFIG, AnalysisEngineConfiguration.builder()
      .addEnabledLanguage(Language.TS).build());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(JAVASCRIPT_PLUGIN_KEY, TypeScriptSensor.class);
  }

  @Test
  void dont_install_typescript_sensor_if_typescript_language_not_enabled_in_connected_mode() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new FakePlugin()));

    underTest = new AnalysisExtensionInstaller(RUNTIME, loadedPlugins, EMPTY_CONFIG, AnalysisEngineConfiguration.builder()
      .addEnabledLanguage(Language.JS).build());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container, never()).addExtension(FAKE_PLUGIN_KEY, TypeScriptSensor.class);

    assertThat(logTester.logs(ClientLogOutput.Level.DEBUG)).contains("TypeScript sensor excluded");
  }

  @Test
  void install_typescript_sensor_if_typescript_language_enabled_in_standalone() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(JAVASCRIPT_PLUGIN_KEY, new FakePlugin()));

    underTest = new AnalysisExtensionInstaller(RUNTIME, loadedPlugins, EMPTY_CONFIG, AnalysisEngineConfiguration.builder()
      .addEnabledLanguage(Language.TS).build());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(JAVASCRIPT_PLUGIN_KEY, TypeScriptSensor.class);
  }

  @Test
  void provide_sonarlint_context_for_plugin_definition() {
    var pluginInstance = new PluginStoringSonarLintPluginApiVersion();
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, pluginInstance));

    underTest = new AnalysisExtensionInstaller(RUNTIME, loadedPlugins, EMPTY_CONFIG, AnalysisEngineConfiguration.builder().build());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    assertThat(pluginInstance.sonarLintPluginApiVersion).isEqualTo(PLUGIN_API_VERSION);
    assertThat(pluginInstance.clientPid).isEqualTo(FAKE_PID);
  }

  @Test
  void log_when_plugin_throws() {
    when(loadedPlugins.getPluginInstancesByKeys()).thenReturn(Map.of(FAKE_PLUGIN_KEY, new ThrowingPlugin()));

    underTest = new AnalysisExtensionInstaller(RUNTIME, loadedPlugins, EMPTY_CONFIG, AnalysisEngineConfiguration.builder().build());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).contains("Error loading components for plugin 'foo'");
  }

  private static class FakePlugin implements Plugin {
    private final Object component;

    private FakePlugin() {
      this(FakeSonarLintDefaultLifespanComponent.class);
    }

    public FakePlugin(Object component) {
      this.component = component;
    }

    @Override
    public void define(Context context) {
      context.addExtension(component);
      context.addExtension(FakeSensor.class);
      context.addExtension(TypeScriptSensor.class);
    }

  }

  private static class ThrowingPlugin implements Plugin {
    @Override
    public void define(Context context) {
      throw new Error();
    }

  }

  private static class PluginStoringSonarLintPluginApiVersion implements Plugin {
    Version sonarLintPluginApiVersion;
    long clientPid;

    @Override
    public void define(Context context) {
      if (context.getRuntime() instanceof SonarLintRuntime) {
        sonarLintPluginApiVersion = ((SonarLintRuntime) context.getRuntime()).getSonarLintPluginApiVersion();
        clientPid = ((SonarLintRuntime) context.getRuntime()).getClientPid();
      }
    }

  }

  @SonarLintSide
  private static class FakeSonarLintDefaultLifespanComponent {
  }

  @SonarLintSide(lifespan = SonarLintSide.SINGLE_ANALYSIS)
  private static class FakeSonarLintSingleAnalysisLifespanComponent {
  }

  @SonarLintSide(lifespan = SonarLintSide.MULTIPLE_ANALYSES)
  private static class FakeSonarLintMultipleAnalysisLifespanComponent {
  }

  @SonarLintSide(lifespan = "MODULE")
  private static class FakeSonarLintModuleLifespanComponent {
  }

  @SonarLintSide(lifespan = "INSTANCE")
  private static class FakeSonarLintInstanceLifespanComponent {
  }

  private static class FakeSensor implements Sensor {

    @Override
    public void describe(SensorDescriptor descriptor) {

    }

    @Override
    public void execute(SensorContext context) {
    }
  }

  private static class TypeScriptSensor implements Sensor {

    @Override
    public void describe(SensorDescriptor descriptor) {

    }

    @Override
    public void execute(SensorContext context) {
    }
  }

}
