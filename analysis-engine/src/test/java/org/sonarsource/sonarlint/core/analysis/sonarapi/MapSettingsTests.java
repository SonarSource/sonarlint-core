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

import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.System2;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapSettingsTests {

  private PropertyDefinitions definitions;

  @Properties({
    @Property(key = "hello", name = "Hello", defaultValue = "world"),
    @Property(key = "date", name = "Date", defaultValue = "2010-05-18"),
    @Property(key = "datetime", name = "DateTime", defaultValue = "2010-05-18T15:50:45+0100"),
    @Property(key = "boolean", name = "Boolean", defaultValue = "true"),
    @Property(key = "falseboolean", name = "False Boolean", defaultValue = "false"),
    @Property(key = "integer", name = "Integer", defaultValue = "12345"),
    @Property(key = "array", name = "Array", defaultValue = "one,two,three"),
    @Property(key = "multi_values", name = "Array", defaultValue = "1,2,3", multiValues = true),
    @Property(key = "sonar.jira", name = "Jira Server", type = PropertyType.PROPERTY_SET, propertySetKey = "jira"),
    @Property(key = "newKey", name = "New key", deprecatedKey = "oldKey"),
    @Property(key = "newKeyWithDefaultValue", name = "New key with default value", deprecatedKey = "oldKeyWithDefaultValue", defaultValue = "default_value"),
    @Property(key = "new_multi_values", name = "New multi values", defaultValue = "1,2,3", multiValues = true, deprecatedKey = "old_multi_values")
  })
  private static class Init {
  }

  @BeforeEach
  void init_definitions() {
    definitions = new PropertyDefinitions(System2.INSTANCE);
    definitions.addComponent(Init.class);
  }

  @Test
  void set_accepts_empty_value_and_trims_it() {
    var random = new Random();
    var key = randomAlphanumeric(3);

    var underTest = new MapSettings(Map.of(key, blank(random)));

    assertThat(underTest.getString(key)).isEmpty();
  }

  @Test
  void default_values_should_be_loaded_from_definitions() {
    var settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getDefaultValue("hello")).isEqualTo("world");
  }

  @Test
  void set_property_string_array_trims_key() {
    var key = randomAlphanumeric(3);

    var random = new Random();
    var blankBefore = blank(random);
    var blankAfter = blank(random);

    var underTest = new MapSettings(new PropertyDefinitions(System2.INSTANCE, singletonList(PropertyDefinition.builder(key).multiValues(true).build())),
      Map.of(blankBefore + key + blankAfter, "1,2"));

    assertThat(underTest.hasKey(key)).isTrue();
  }

  private static String blank(Random random) {
    var b = new StringBuilder();
    IntStream.range(0, random.nextInt(3)).mapToObj(s -> " ").forEach(b::append);
    return b.toString();
  }

  @Test
  void setProperty_methods_trims_value() {
    var random = new Random();
    var blankBefore = blank(random);
    var blankAfter = blank(random);
    var key = randomAlphanumeric(3);
    var value = randomAlphanumeric(3);

    var underTest = new MapSettings(Map.of(key, blankBefore + value + blankAfter));

    assertThat(underTest.getString(key)).isEqualTo(value);
  }

  @Test
  void set_property_int() {
    var settings = new MapSettings(Map.of("foo", "123"));
    assertThat(settings.getInt("foo")).isEqualTo(123);
    assertThat(settings.getString("foo")).isEqualTo("123");
    assertThat(settings.getBoolean("foo")).isFalse();
  }

  @Test
  void default_number_values_are_zero() {
    var settings = new MapSettings(Map.of());
    assertThat(settings.getInt("foo")).isZero();
    assertThat(settings.getLong("foo")).isZero();
  }

  @Test
  void getInt_value_must_be_valid() {
    var settings = new MapSettings(Map.of("foo", "not a number"));

    assertThrows(NumberFormatException.class, () -> settings.getInt("foo"));
  }

  @Test
  void all_values_should_be_trimmed_set_property() {
    var settings = new MapSettings(Map.of("foo", "   FOO "));
    assertThat(settings.getString("foo")).isEqualTo("FOO");
  }

  @Test
  void test_get_default_value() {
    var settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getDefaultValue("unknown")).isNull();
  }

  @Test
  void test_get_string() {
    var settings = new MapSettings(definitions, Map.of("hello", "Russia"));
    assertThat(settings.getString("hello")).isEqualTo("Russia");
  }

  @Test
  void test_get_date() {
    var settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getDate("unknown")).isNull();
    assertThat(settings.getDate("date").getDate()).isEqualTo(18);
    assertThat(settings.getDate("date").getMonth()).isEqualTo(4);
  }

  @Test
  void test_get_date_not_found() {
    var settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getDate("unknown")).isNull();
  }

  @Test
  void test_get_datetime() {
    var settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getDateTime("unknown")).isNull();
    assertThat(settings.getDateTime("datetime").getDate()).isEqualTo(18);
    assertThat(settings.getDateTime("datetime").getMonth()).isEqualTo(4);
    assertThat(settings.getDateTime("datetime").getMinutes()).isEqualTo(50);
  }

  @Test
  void test_get_double() {
    var settings = new MapSettings(Map.of("from_string", "3.14159"));
    assertThat(settings.getDouble("from_string")).isEqualTo(3.14159, Offset.offset(0.00001));
    assertThat(settings.getDouble("unknown")).isNull();
  }

  @Test
  void test_get_float() {
    var settings = new MapSettings(Map.of("from_string", "3.14159"));
    assertThat(settings.getDouble("from_string")).isEqualTo(3.14159f, Offset.offset(0.00001));
    assertThat(settings.getDouble("unknown")).isNull();
  }

  @Test
  void test_get_bad_float() {
    var settings = new MapSettings(Map.of("foo", "bar"));

    var thrown = assertThrows(IllegalStateException.class, () -> settings.getFloat("foo"));
    assertThat(thrown).hasMessage("The property 'foo' is not a float value");
  }

  @Test
  void test_get_bad_double() {
    var settings = new MapSettings(Map.of("foo", "bar"));

    var thrown = assertThrows(IllegalStateException.class, () -> settings.getDouble("foo"));
    assertThat(thrown).hasMessage("The property 'foo' is not a double value");
  }

  @Test
  void getStringArray() {
    var settings = new MapSettings(definitions, Map.of());
    var array = settings.getStringArray("array");
    assertThat(array).isEqualTo(new String[] {"one", "two", "three"});
  }

  @Test
  void getStringArray_no_value() {
    var settings = new MapSettings(Map.of());
    var array = settings.getStringArray("array");
    assertThat(array).isEmpty();
  }

  @Test
  void shouldTrimArray() {
    var settings = new MapSettings(Map.of("foo", "  one,  two, three  "));
    var array = settings.getStringArray("foo");
    assertThat(array).isEqualTo(new String[] {"one", "two", "three"});
  }

  @Test
  void shouldKeepEmptyValuesWhenSplitting() {
    var settings = new MapSettings(Map.of("foo", "  one,  , two"));
    var array = settings.getStringArray("foo");
    assertThat(array).isEqualTo(new String[] {"one", "", "two"});
  }

  @Test
  void testDefaultValueOfGetString() {
    var settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getString("hello")).isEqualTo("world");
  }

  @Test
  void set_property_boolean() {
    var settings = new MapSettings(Map.of("foo", "true", "bar", "false"));
    assertThat(settings.getBoolean("foo")).isTrue();
    assertThat(settings.getBoolean("bar")).isFalse();
    assertThat(settings.getString("foo")).isEqualTo("true");
    assertThat(settings.getString("bar")).isEqualTo("false");
  }

  @Test
  void ignore_case_of_boolean_values() {
    var settings = new MapSettings(Map.of("foo", "true", "bar", "TRUE",
      // labels in UI
      "baz", "True"));

    assertThat(settings.getBoolean("foo")).isTrue();
    assertThat(settings.getBoolean("bar")).isTrue();
    assertThat(settings.getBoolean("baz")).isTrue();
  }

  @Test
  void get_boolean() {
    var settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getBoolean("boolean")).isTrue();
    assertThat(settings.getBoolean("falseboolean")).isFalse();
    assertThat(settings.getBoolean("unknown")).isFalse();
    assertThat(settings.getBoolean("hello")).isFalse();
  }

  @Test
  void shouldCreateByIntrospectingComponent() {
    var settings = new MapSettings(Map.of());
    settings.getDefinitions().addComponent(MyComponent.class);

    // property definition has been loaded, ie for default value
    assertThat(settings.getDefaultValue("foo")).isEqualTo("bar");
  }

  @Property(key = "foo", name = "Foo", defaultValue = "bar")
  public static class MyComponent {

  }

  @Test
  void getStringLines_no_value() {
    Assertions.assertThat(new MapSettings(Map.of()).getStringLines("foo")).isEmpty();
  }

  @Test
  void getStringLines_single_line() {
    var settings = new MapSettings(Map.of("foo", "the line"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"the line"});
  }

  @Test
  void getStringLines_linux() {
    var settings = new MapSettings(Map.of("foo", "one\ntwo"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});

    settings = new MapSettings(Map.of("foo", "one\ntwo\n"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});
  }

  @Test
  void getStringLines_windows() {
    var settings = new MapSettings(Map.of("foo", "one\r\ntwo"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});

    settings = new MapSettings(Map.of("foo", "one\r\ntwo\r\n"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});
  }

  @Test
  void getStringLines_mix() {
    var settings = new MapSettings(Map.of("foo", "one\r\ntwo\nthree"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two", "three"});
  }

  @Test
  void getKeysStartingWith() {
    var settings = new MapSettings(Map.of("sonar.jdbc.url", "foo", "sonar.jdbc.username", "bar", "sonar.security", "admin"));

    assertThat(settings.getKeysStartingWith("sonar")).containsOnly("sonar.jdbc.url", "sonar.jdbc.username", "sonar.security");
    assertThat(settings.getKeysStartingWith("sonar.jdbc")).containsOnly("sonar.jdbc.url", "sonar.jdbc.username");
    assertThat(settings.getKeysStartingWith("other")).isEmpty();
  }

  @Test
  void should_fallback_deprecated_key_to_default_value_of_new_key() {
    var settings = new MapSettings(definitions, Map.of());

    assertThat(settings.getString("newKeyWithDefaultValue")).isEqualTo("default_value");
    assertThat(settings.getString("oldKeyWithDefaultValue")).isEqualTo("default_value");
  }

  @Test
  void should_fallback_deprecated_key_to_new_key() {
    var settings = new MapSettings(definitions, Map.of("newKey", "value of newKey"));

    assertThat(settings.getString("newKey")).isEqualTo("value of newKey");
    assertThat(settings.getString("oldKey")).isEqualTo("value of newKey");
  }

  @Test
  void should_load_value_of_deprecated_key() {
    // it's used for example when deprecated settings are set through command-line
    var settings = new MapSettings(definitions, Map.of("oldKey", "value of oldKey"));

    assertThat(settings.getString("newKey")).isEqualTo("value of oldKey");
    assertThat(settings.getString("oldKey")).isEqualTo("value of oldKey");
  }

  @Test
  void should_load_values_of_deprecated_key() {
    var settings = new MapSettings(definitions, Map.of("oldKey", "a,b"));

    assertThat(settings.getStringArray("newKey")).containsOnly("a", "b");
    assertThat(settings.getStringArray("oldKey")).containsOnly("a", "b");
  }

  @Test
  void should_support_deprecated_props_with_multi_values() {
    var settings = new MapSettings(definitions, Map.of("new_multi_values", " A , B "));
    assertThat(settings.getStringArray("new_multi_values")).isEqualTo(new String[] {"A", "B"});
    assertThat(settings.getStringArray("old_multi_values")).isEqualTo(new String[] {"A", "B"});
  }
}
