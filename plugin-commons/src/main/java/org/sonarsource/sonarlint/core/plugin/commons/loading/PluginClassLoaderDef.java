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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonar.classloader.Mask;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Temporary information about the classLoader to be created for a plugin (or a group of plugins).
 */
class PluginClassLoaderDef {

  private final String basePluginKey;
  private final Map<String, String> mainClassesByPluginKey = new HashMap<>();
  private final List<File> files = new ArrayList<>();
  private final Mask mask = new Mask();

  PluginClassLoaderDef(String basePluginKey) {
    this.basePluginKey = basePluginKey;
  }

  String getBasePluginKey() {
    return basePluginKey;
  }

  List<File> getFiles() {
    return files;
  }

  void addFiles(Collection<File> f) {
    this.files.addAll(f);
  }

  Mask getExportMask() {
    return mask;
  }

  Map<String, String> getMainClassesByPluginKey() {
    return mainClassesByPluginKey;
  }

  void addMainClass(String pluginKey, @Nullable String mainClass) {
    if (isNotEmpty(mainClass)) {
      mainClassesByPluginKey.put(pluginKey, mainClass);
    }
  }

}
