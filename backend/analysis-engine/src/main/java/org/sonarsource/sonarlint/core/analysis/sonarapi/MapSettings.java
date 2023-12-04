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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.sonarsource.sonarlint.core.analysis.sonarapi.MultivalueProperty.parseAsCsv;

public class MapSettings extends Settings {

  private final Map<String, String> props;
  private final ConfigurationBridge configurationBridge;
  private final PropertyDefinitions definitions;

  // For testing
  public MapSettings(Map<String, String> props) {
    this(new PropertyDefinitions(System2.INSTANCE), props);
  }

  public MapSettings(PropertyDefinitions definitions, Map<String, String> props) {
    this.props = props.entrySet().stream()
      .collect(
        toUnmodifiableMap(e -> definitions.validKey(e.getKey()), e -> trim(e.getValue())));
    this.definitions = definitions;
    configurationBridge = new ConfigurationBridge(this);
  }

  protected Optional<String> get(String key) {
    return Optional.ofNullable(props.get(key));
  }

  public Map<String, String> getProperties() {
    return props;
  }

  /**
   * The value that overrides the default value. It
   * may be encrypted with a secret key. Use {@link #getString(String)} to get
   * the effective and decrypted value.
   *
   * @since 6.1
   */
  public Optional<String> getRawString(String key) {
    return get(definitions.validKey(requireNonNull(key)));
  }

  /**
   * All the property definitions declared by core and plugins.
   */
  public PropertyDefinitions getDefinitions() {
    return definitions;
  }

  /**
   * The definition related to the specified property. It may
   * be empty.
   *
   * @since 6.1
   */
  public Optional<PropertyDefinition> getDefinition(String key) {
    return Optional.ofNullable(definitions.get(key));
  }

  /**
   * @return {@code true} if the property has a non-default value, else {@code false}.
   */
  @Override
  public boolean hasKey(String key) {
    return getRawString(key).isPresent();
  }

  @CheckForNull
  public String getDefaultValue(String key) {
    return definitions.getDefaultValue(key);
  }

  public boolean hasDefaultValue(String key) {
    return StringUtils.isNotEmpty(getDefaultValue(key));
  }

  /**
   * The effective value of the specified property. Can return
   * {@code null} if the property is not set and has no
   * defined default value.
   * <p>
   * If the property is encrypted with a secret key,
   * then the returned value is decrypted.
   * </p>
   *
   * @throws IllegalStateException if value is encrypted but fails to be decrypted.
   */
  @CheckForNull
  @Override
  public String getString(String key) {
    var effectiveKey = definitions.validKey(key);
    // default values cannot be encrypted, so return value as-is.
    return getRawString(effectiveKey)
      .orElseGet(() -> getDefaultValue(effectiveKey));
  }

  /**
   * Effective value as boolean. It is {@code false} if {@link #getString(String)}
   * does not return {@code "true"}, even if it's not a boolean representation.
   *
   * @return {@code true} if the effective value is {@code "true"}, else {@code false}.
   */
  @Override
  public boolean getBoolean(String key) {
    var value = getString(key);
    return StringUtils.isNotEmpty(value) && Boolean.parseBoolean(value);
  }

  /**
   * Effective value as {@code int}.
   *
   * @return the value as {@code int}. If the property does not have value nor default value, then {@code 0} is returned.
   * @throws NumberFormatException if value is not empty and is not a parsable integer
   */
  @Override
  public int getInt(String key) {
    var value = getString(key);
    if (StringUtils.isNotEmpty(value)) {
      return Integer.parseInt(value);
    }
    return 0;
  }

  /**
   * Effective value as {@code long}.
   *
   * @return the value as {@code long}. If the property does not have value nor default value, then {@code 0L} is returned.
   * @throws NumberFormatException if value is not empty and is not a parsable {@code long}
   */
  @Override
  public long getLong(String key) {
    var value = getString(key);
    if (StringUtils.isNotEmpty(value)) {
      return Long.parseLong(value);
    }
    return 0L;
  }

  /**
   * Effective value as {@link Date}, without time fields. Format is {@link DateUtils#DATE_FORMAT}.
   *
   * @return the value as a {@link Date}. If the property does not have value nor default value, then {@code null} is returned.
   * @throws RuntimeException if value is not empty and is not in accordance with {@link DateUtils#DATE_FORMAT}.
   */
  @CheckForNull
  @Override
  public Date getDate(String key) {
    var value = getString(key);
    if (StringUtils.isNotEmpty(value)) {
      return DateUtils.parseDate(value);
    }
    return null;
  }

  /**
   * Effective value as {@link Date}, with time fields. Format is {@link DateUtils#DATETIME_FORMAT}.
   *
   * @return the value as a {@link Date}. If the property does not have value nor default value, then {@code null} is returned.
   * @throws RuntimeException if value is not empty and is not in accordance with {@link DateUtils#DATETIME_FORMAT}.
   */
  @CheckForNull
  @Override
  public Date getDateTime(String key) {
    var value = getString(key);
    if (StringUtils.isNotEmpty(value)) {
      return DateUtils.parseDateTime(value);
    }
    return null;
  }

  /**
   * Effective value as {@code Float}.
   *
   * @return the value as {@code Float}. If the property does not have value nor default value, then {@code null} is returned.
   * @throws NumberFormatException if value is not empty and is not a parsable number
   */
  @CheckForNull
  @Override
  public Float getFloat(String key) {
    var value = getString(key);
    if (StringUtils.isNotEmpty(value)) {
      try {
        return Float.valueOf(value);
      } catch (NumberFormatException e) {
        throw new IllegalStateException(String.format("The property '%s' is not a float value", key));
      }
    }
    return null;
  }

  /**
   * Effective value as {@code Double}.
   *
   * @return the value as {@code Double}. If the property does not have value nor default value, then {@code null} is returned.
   * @throws NumberFormatException if value is not empty and is not a parsable number
   */
  @CheckForNull
  @Override
  public Double getDouble(String key) {
    var value = getString(key);
    if (StringUtils.isNotEmpty(value)) {
      try {
        return Double.valueOf(value);
      } catch (NumberFormatException e) {
        throw new IllegalStateException(String.format("The property '%s' is not a double value", key));
      }
    }
    return null;
  }

  /**
   * Value is split by comma and trimmed. Never returns null.
   * <br>
   * Examples :
   * <ul>
   * <li>"one,two,three " -&gt; ["one", "two", "three"]</li>
   * <li>"  one, two, three " -&gt; ["one", "two", "three"]</li>
   * <li>"one, , three" -&gt; ["one", "", "three"]</li>
   * </ul>
   */
  @Override
  public String[] getStringArray(String key) {
    var effectiveKey = definitions.validKey(key);
    var def = getDefinition(effectiveKey);
    if ((def.isPresent()) && (def.get().multiValues())) {
      var value = getString(key);
      if (value == null) {
        return ArrayUtils.EMPTY_STRING_ARRAY;
      }

      return parseAsCsv(effectiveKey, value);
    }

    return getStringArrayBySeparator(key, ",");
  }

  /**
   * Value is split by carriage returns.
   *
   * @return non-null array of lines. The line termination characters are excluded.
   * @since 3.2
   */
  @Override
  public String[] getStringLines(String key) {
    var value = getString(key);
    if (StringUtils.isEmpty(value)) {
      return new String[0];
    }
    return value.split("\r?\n|\r", -1);
  }

  /**
   * Value is split and trimmed.
   */
  @Override
  public String[] getStringArrayBySeparator(String key, String separator) {
    var value = getString(key);
    if (value != null) {
      var strings = StringUtils.splitByWholeSeparator(value, separator);
      var result = new String[strings.length];
      for (var index = 0; index < strings.length; index++) {
        result[index] = trim(strings[index]);
      }
      return result;
    }
    return ArrayUtils.EMPTY_STRING_ARRAY;
  }

  @Override
  public List<String> getKeysStartingWith(String prefix) {
    return getProperties().keySet().stream()
      .filter(key -> StringUtils.startsWith(key, prefix))
      .collect(Collectors.toList());
  }

  /**
   * @return a {@link Configuration} proxy on top of this existing {@link Settings} implementation. Changes are reflected in the {@link Configuration} object.
   * @since 6.5
   */
  public Configuration asConfig() {
    return configurationBridge;
  }
}
