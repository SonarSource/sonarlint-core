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
package org.sonarsource.sonarlint.core.container.global;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.Language;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ExtensionInstallerTests {

  @RegisterExtension
  LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private static final Configuration CONFIG = new MapSettings().asConfig();
  private static final SonarRuntime RUNTIME = new SonarLintRuntimeImpl(Version.create(8, 0));
  private ExtensionInstaller underTest;
  private PluginRepository pluginRepository;
  private PluginVersionChecker pluginVersionChecker;
  private ComponentContainer container;

  @BeforeEach
  public void prepare() {
    pluginRepository = mock(PluginRepository.class);
    pluginVersionChecker = mock(PluginVersionChecker.class);
    container = mock(ComponentContainer.class);
    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, pluginVersionChecker, StandaloneGlobalConfiguration.builder().build());
  }

  @Test
  public void install_sonarlint_extensions_for_compatible_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginRepository.getPluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin());
    underTest.install(container, false);

    verify(container).addExtension(pluginInfo, FakeComponent.class);
  }

  @Test
  public void install_sensors_for_sonarsource_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginVersionChecker.getMinimumVersion("foo")).thenReturn("1.0");

    when(pluginRepository.getPluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin());
    underTest.install(container, false);

    verify(container).addExtension(pluginInfo, FakeSensor.class);
  }

  @Test
  public void dont_install_sensors_for_non_sonarsource_plugins() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginVersionChecker.getMinimumVersion("foo")).thenReturn(null);

    when(pluginRepository.getPluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin());
    underTest.install(container, false);

    verify(container, never()).addExtension(pluginInfo, FakeSensor.class);
  }

  @Test
  public void install_typescript_sensor_if_typescript_language_enabled_in_connected_mode() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginVersionChecker.getMinimumVersion("foo")).thenReturn("1.0");

    when(pluginRepository.getPluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin());

    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, pluginVersionChecker, ConnectedGlobalConfiguration.builder()
      .addEnabledLanguage(Language.TS).build());

    underTest.install(container, false);

    verify(container).addExtension(pluginInfo, TypeScriptSensor.class);
  }

  @Test
  public void dont_install_typescript_sensor_if_typescript_language_not_enabled_in_connected_mode() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginVersionChecker.getMinimumVersion("foo")).thenReturn("1.0");

    when(pluginRepository.getPluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin());

    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, pluginVersionChecker, ConnectedGlobalConfiguration.builder()
      .addEnabledLanguage(Language.JS).build());

    underTest.install(container, false);

    verify(container, never()).addExtension(pluginInfo, TypeScriptSensor.class);

    assertThat(logTester.logs(LoggerLevel.DEBUG)).contains("TypeScript sensor excluded");
  }

  @Test
  public void install_typescript_sensor_if_typescript_language_enabled_in_standalone() {
    PluginInfo pluginInfo = new PluginInfo("foo");
    pluginInfo.setSonarLintSupported(true);
    when(pluginVersionChecker.getMinimumVersion("foo")).thenReturn("1.0");

    when(pluginRepository.getPluginInfos()).thenReturn(singletonList(pluginInfo));
    when(pluginRepository.getPluginInstance("foo")).thenReturn(new FakePlugin());

    underTest = new ExtensionInstaller(RUNTIME, pluginRepository, CONFIG, pluginVersionChecker, StandaloneGlobalConfiguration.builder()
      .addEnabledLanguage(Language.TS).build());

    underTest.install(container, false);

    verify(container).addExtension(pluginInfo, TypeScriptSensor.class);
  }

  private static class FakePlugin implements Plugin {

    @Override
    public void define(Context context) {
      context.addExtension(FakeComponent.class);
      context.addExtension(FakeSensor.class);
      context.addExtension(TypeScriptSensor.class);
    }

  }

  @SonarLintSide
  private static class FakeComponent {
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
