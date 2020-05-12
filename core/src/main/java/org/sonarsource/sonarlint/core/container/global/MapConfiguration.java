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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.container.analysis.ServerConfigurationProvider;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.trim;

public class MapConfiguration extends ServerConfigurationProvider.ServerConfiguration {

  public MapConfiguration() {
    this(new PropertyDefinitions(System2.INSTANCE));
  }

  public MapConfiguration(PropertyDefinitions definitions) {
    super(definitions, Collections.emptyMap());
  }

  @Override
  public MapConfiguration setProperty(String key, String value) {
    return (MapConfiguration) super.setProperty(key, value);
  }

  public MapConfiguration setProperty(String key, Boolean value) {
    return setProperty(key, value == null ? null : String.valueOf(value));
  }

  public MapConfiguration setProperty(String key, Integer value) {
    return setProperty(key, value == null ? null : String.valueOf(value));
  }

  public MapConfiguration setProperty(String key, Long value) {
    return setProperty(key, value == null ? null : String.valueOf(value));
  }

  public MapConfiguration setProperty(String key, @Nullable Float value) {
    return setProperty(key, value == null ? null : String.valueOf(value));
  }

  public MapConfiguration setProperty(String key, @Nullable Double value) {
    return setProperty(key, value == null ? null : String.valueOf(value));
  }

  public String[] getStringLines(String key) {
    String value = super.get(key).orElse(null);
    if (StringUtils.isEmpty(value)) {
      return new String[0];
    }
    return value.split("\r?\n|\r", -1);
  }

  public MapConfiguration setProperty(String key, @Nullable String[] values) {
    requireNonNull(key, "key can't be null");
    String effectiveKey = key.trim();
    Optional<PropertyDefinition> def = getDefinition(effectiveKey);
    if (!def.isPresent() || (!def.get().multiValues())) {
      throw new IllegalStateException("Fail to set multiple values on a single value property " + key);
    }

    String text = null;
    if (values != null) {
      List<String> escaped = new ArrayList<>();
      for (String value : values) {
        if (null != value) {
          escaped.add(value.replace(",", "%2C"));
        } else {
          escaped.add("");
        }
      }

      String escapedValue = escaped.stream().collect(Collectors.joining(","));
      text = trim(escapedValue);
    }
    return setProperty(key, text);
  }

  /**
   * @see #setProperty(String, String)
   */
  public MapConfiguration setProperty(String key, @Nullable Date date) {
    return setProperty(key, date, false);
  }

  public MapConfiguration setProperty(String key, @Nullable Date date, boolean includeTime) {
    if (date == null) {
      return (MapConfiguration) super.setProperty(key, null);
    }
    return setProperty(key, includeTime ? DateUtils.formatDateTime(date) : DateUtils.formatDate(date));
  }

}
