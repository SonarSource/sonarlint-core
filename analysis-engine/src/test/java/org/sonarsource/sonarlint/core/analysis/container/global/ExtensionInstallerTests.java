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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.util.Arrays;
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
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.analysis.api.GlobalAnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.Language;
import org.sonarsource.sonarlint.core.analysis.container.ComponentContainer;
import org.sonarsource.sonarlint.core.analysis.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.analysis.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.analysis.plugin.PluginRepository;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ExtensionInstallerTests {

  @RegisterExtension
  LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private static final Configuration CONFIG = new MapSettings(Map.of()).asConfig();
  private static final Version PLUGIN_API_VERSION = Version.create(5, 4, 0);
  private static final long FAKE_PID = 123L;
  private static final SonarLintRuntime RUNTIME = new SonarLintRuntimeImpl(Version.create(8, 0), PLUGIN_API_VERSION, FAKE_PID);
  private ExtensionInstaller underTest;
  private PluginRepository pluginRepository;
  private ComponentContainer container;

  @BeforeEach
  public void prepare() {
    pluginRepository = mock(PluginRepository.class);
    container = mock(ComponentContainer.class);
    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, GlobalAnalysisConfiguration.builder().build());
  }

  @Test
  void install_sonarlintside_extensions_with_default_lifespan_in_analysis_container_for_compatible_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(pluginInfo, FakeSonarLintDefaultLifespanComponent.class);
  }

  @Test
  void install_sonarlintside_extensions_with_single_analysis_lifespan_in_analysis_container_for_compatible_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin(FakeSonarLintSingleAnalysisLifespanComponent.class));

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(pluginInfo, FakeSonarLintSingleAnalysisLifespanComponent.class);
  }

  @Test
  void install_sonarlintside_extensions_with_multiple_analysis_lifespan_in_global_container_for_compatible_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin(FakeSonarLintMultipleAnalysisLifespanComponent.class));

    underTest.install(container, ContainerLifespan.INSTANCE);

    verify(container).addExtension(pluginInfo, FakeSonarLintMultipleAnalysisLifespanComponent.class);
  }

  @Test
  void install_sonarlintside_extensions_with_instance_lifespan_in_global_container_for_compatible_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin(FakeSonarLintInstanceLifespanComponent.class));

    underTest.install(container, ContainerLifespan.INSTANCE);

    verify(container).addExtension(pluginInfo, FakeSonarLintInstanceLifespanComponent.class);
  }

  @Test
  void dont_install_sonarlintside_extensions_with_multiple_analysis_lifespan_in_analysis_container_for_compatible_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin(FakeSonarLintMultipleAnalysisLifespanComponent.class));

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verifyNoInteractions(container);
  }

  @Test
  void dont_install_sonarlintside_extensions_with_single_analysis_lifespan_in_global_container_for_compatible_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin(FakeSonarLintSingleAnalysisLifespanComponent.class));

    underTest.install(container, ContainerLifespan.INSTANCE);

    verifyNoInteractions(container);
  }

  @Test
  void install_sonarlintside_extensions_with_module_lifespan_in_module_container_for_compatible_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin(FakeSonarLintModuleLifespanComponent.class));

    underTest.install(container, ContainerLifespan.MODULE);

    verify(container).addExtension(pluginInfo, FakeSonarLintModuleLifespanComponent.class);
  }

  @Test
  void install_sensors_for_sonarsource_plugins() {
    PluginInfo pluginInfo = new PluginInfo("java");
    pluginInfo.setSonarLintSupported(true);

    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("java")).thenReturn(new FakePlugin());
    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(pluginInfo, FakeSensor.class);
  }

  @Test
  void dont_install_sensors_for_non_sonarsource_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);

    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin());
    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container, never()).addExtension(pluginInfo, FakeSensor.class);
  }

  @Test
  void install_typescript_sensor_if_typescript_language_enabled_in_connected_mode() {
    PluginInfo pluginInfo = new PluginInfo("javascript");
    pluginInfo.setSonarLintSupported(true);

    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("javascript")).thenReturn(new FakePlugin());

    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, GlobalAnalysisConfiguration.builder()
      .addEnabledLanguage(Language.TS).build());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(pluginInfo, TypeScriptSensor.class);
  }

  @Test
  void dont_install_typescript_sensor_if_typescript_language_not_enabled_in_connected_mode() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);

    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin());

    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, GlobalAnalysisConfiguration.builder()
      .addEnabledLanguage(Language.JS).build());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container, never()).addExtension(pluginInfo, TypeScriptSensor.class);

    assertThat(logTester.logs(LoggerLevel.DEBUG)).contains("TypeScript sensor excluded");
  }

  @Test
  void install_typescript_sensor_if_typescript_language_enabled_in_standalone() {
    PluginInfo pluginInfo = new PluginInfo("javascript");
    pluginInfo.setSonarLintSupported(true);

    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("javascript")).thenReturn(new FakePlugin());

    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, GlobalAnalysisConfiguration.builder()
      .addEnabledLanguage(Language.TS).build());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(pluginInfo, TypeScriptSensor.class);
  }

  @Test
  void only_install_component_of_embedded_plugins() {
    PluginInfo pluginInfoNotEmbedded = new PluginInfo("notembedded");
    pluginInfoNotEmbedded.setSonarLintSupported(true);
    pluginInfoNotEmbedded.setEmbedded(false);

    PluginInfo pluginInfoEmbedded = new PluginInfo("embedded");
    pluginInfoEmbedded.setSonarLintSupported(true);
    pluginInfoEmbedded.setEmbedded(true);

    when(pluginRepository.getActivePluginInfos()).thenReturn(Arrays.asList(pluginInfoNotEmbedded, pluginInfoEmbedded));
    when(pluginRepository.getPluginInstance("notembedded")).thenReturn(new FakePlugin());
    when(pluginRepository.getPluginInstance("embedded")).thenReturn(new FakePlugin());

    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, GlobalAnalysisConfiguration.builder().build());

    underTest.installEmbeddedOnly(container, ContainerLifespan.ANALYSIS);

    verify(container).addExtension(pluginInfoEmbedded, FakeSonarLintDefaultLifespanComponent.class);
    verifyNoMoreInteractions(container);
  }

  @Test
  void provide_sonarlint_context_for_plugin_definition() {
    PluginInfo plugin = new PluginInfo("plugin");
    plugin.setSonarLintSupported(true);
    plugin.setEmbedded(false);
    when(pluginRepository.getActivePluginInfos()).thenReturn(singletonList(plugin));
    PluginStoringSonarLintPluginApiVersion pluginInstance = new PluginStoringSonarLintPluginApiVersion();
    when(pluginRepository.getPluginInstance("plugin")).thenReturn(pluginInstance);
    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, GlobalAnalysisConfiguration.builder().build());

    underTest.install(container, ContainerLifespan.ANALYSIS);

    assertThat(pluginInstance.sonarLintPluginApiVersion).isEqualTo(PLUGIN_API_VERSION);
    assertThat(pluginInstance.clientPid).isEqualTo(FAKE_PID);
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
