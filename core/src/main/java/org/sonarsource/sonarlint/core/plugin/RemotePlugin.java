/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang.StringUtils;

public class RemotePlugin {
  private String pluginKey;
  private RemotePluginFile file = null;

  public RemotePlugin(String pluginKey) {
    this.pluginKey = pluginKey;
  }

  public static RemotePlugin create(PluginInfo pluginInfo) {
    RemotePlugin result = new RemotePlugin(pluginInfo.getKey());
    result.setFile(pluginInfo.getNonNullJarFile());
    return result;
  }

  public static RemotePlugin unmarshal(String row) {
    String[] fields = StringUtils.split(row, ",");
    RemotePlugin result = new RemotePlugin(fields[0]);
    if (fields.length >= 2) {
      String[] nameAndHash = StringUtils.split(fields[1], "|");
      result.setFile(nameAndHash[0], nameAndHash[1]);
    }
    return result;
  }

  public String marshal() {
    StringBuilder sb = new StringBuilder();
    sb.append(pluginKey);
    sb.append(",").append(file.getFilename()).append("|").append(file.getHash());
    return sb.toString();
  }

  public String getKey() {
    return pluginKey;
  }

  public RemotePlugin setFile(String filename, String hash) {
    file = new RemotePluginFile(filename, hash);
    return this;
  }

  public RemotePlugin setFile(File f) {
    try (FileInputStream fis = new FileInputStream(f)) {
      return this.setFile(f.getName(), org.sonarsource.sonarlint.core.util.StringUtils.md5(fis));
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("Fail to compute hash", e);
    }
  }

  public RemotePluginFile file() {
    return file;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RemotePlugin that = (RemotePlugin) o;
    return pluginKey.equals(that.pluginKey);
  }

  @Override
  public int hashCode() {
    return pluginKey.hashCode();
  }
}
