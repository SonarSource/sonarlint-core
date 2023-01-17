/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.Date;
import java.util.List;
import org.sonar.api.config.Settings;

public class EmptySettings extends Settings {

  @Override
  public boolean hasKey(String key) {
    return false;
  }

  @Override
  public String getString(String key) {
    return null;
  }

  @Override
  public boolean getBoolean(String key) {
    return false;
  }

  @Override
  public int getInt(String key) {
    return 0;
  }

  @Override
  public long getLong(String key) {
    return 0;
  }

  @Override
  public Date getDate(String key) {
    return null;
  }

  @Override
  public Date getDateTime(String key) {
    return null;
  }

  @Override
  public Float getFloat(String key) {
    return null;
  }

  @Override
  public Double getDouble(String key) {
    return null;
  }

  @Override
  public String[] getStringArray(String key) {
    return new String[0];
  }

  @Override
  public String[] getStringLines(String key) {
    return new String[0];
  }

  @Override
  public String[] getStringArrayBySeparator(String key, String separator) {
    return new String[0];
  }

  @Override
  public List<String> getKeysStartingWith(String prefix) {
    return List.of();
  }

}
