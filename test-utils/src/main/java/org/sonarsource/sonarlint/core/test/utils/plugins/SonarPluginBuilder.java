/*
 * SonarLint Core - Test Utils
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.test.utils.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import net.bytebuddy.ByteBuddy;
import org.jetbrains.annotations.NotNull;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.sonarlint.core.test.utils.plugins.src.DefaultPlugin;
import org.sonarsource.sonarlint.core.test.utils.plugins.src.DefaultRulesDefinition;
import org.sonarsource.sonarlint.core.test.utils.plugins.src.DefaultSensor;

public class SonarPluginBuilder {

  /**
   * @param pluginKey a plugin key from the whitelist, see {@link org.sonarsource.sonarlint.core.commons.api.SonarLanguage#containsPlugin(String)}
   */
  public static SonarPluginBuilder newSonarPlugin(String pluginKey) {
    return new SonarPluginBuilder(pluginKey);
  }

  private Class<? extends Sensor> sensorClass = DefaultSensor.class;

  private Class<? extends RulesDefinition> rulesDefinitionClass = DefaultRulesDefinition.class;
  private final String pluginKey;

  private SonarPluginBuilder(String pluginKey) {
    this.pluginKey = pluginKey;
  }

  public SonarPluginBuilder withSensor(Class<? extends Sensor> sensorClass) {
    this.sensorClass = sensorClass;
    return this;
  }

  public SonarPluginBuilder withRulesDefinition(Class<? extends RulesDefinition> rulesDefinitionClass) {
    this.rulesDefinitionClass = rulesDefinitionClass;
    return this;
  }

  public Path generate(Path folder) {
    var pluginPath = folder.resolve("my.jar");
    var pluginClass = new ByteBuddy()
      .redefine(DefaultPlugin.class)
      .make();
    var sensorType = new ByteBuddy()
      .redefine(sensorClass)
      .name(DefaultSensor.class.getName())
      .make();
    var rulesDefinitionType = new ByteBuddy()
      .redefine(rulesDefinitionClass)
      .name(DefaultRulesDefinition.class.getName())
      .make();
    try {
      pluginClass.toJar(pluginPath.toFile(), generateManifest());
      sensorType.inject(pluginPath.toFile());
      rulesDefinitionType.inject(pluginPath.toFile());
    } catch (IOException exception) {
      throw new IllegalStateException("Error when generating the plugin", exception);
    }
    return pluginPath;
  }

  @NotNull
  private Manifest generateManifest() {
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(new Attributes.Name("SonarLint-Supported"), "true");
    manifest.getMainAttributes().put(new Attributes.Name("Plugin-Class"), DefaultPlugin.class.getName());
    manifest.getMainAttributes().put(new Attributes.Name("Plugin-Key"), pluginKey);
    manifest.getMainAttributes().put(new Attributes.Name("Plugin-Version"), "10.0.0");
    return manifest;
  }
}
