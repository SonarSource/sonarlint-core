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

import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.System2;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;

public class MapSettingsTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

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

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void init_definitions() {
    definitions = new PropertyDefinitions(System2.INSTANCE);
    definitions.addComponent(Init.class);
  }

  @Test
  public void set_accepts_empty_value_and_trims_it() {
    Random random = new Random();
    String key = randomAlphanumeric(3);

    MapSettings underTest = new MapSettings(Map.of(key, blank(random)));

    assertThat(underTest.getString(key)).isEmpty();
  }

  @Test
  public void default_values_should_be_loaded_from_definitions() {
    MapSettings settings = new MapSettings(definitions, Map.of());

    assertThat(settings.getDefaultValue("hello")).isEqualTo("world");
  }

  private static String blank(Random random) {
    StringBuilder b = new StringBuilder();
    IntStream.range(0, random.nextInt(3)).mapToObj(s -> " ").forEach(b::append);
    return b.toString();
  }

  @Test
  public void default_number_values_are_zero() {
    MapSettings settings = new MapSettings(Map.of());
    assertThat(settings.getInt("foo")).isZero();
    assertThat(settings.getLong("foo")).isZero();
  }

  @Test
  public void getInt_value_must_be_valid() {
    thrown.expect(NumberFormatException.class);

    MapSettings settings = new MapSettings(Map.of("foo", "not a number"));

    settings.getInt("foo");
  }

  @Test
  public void all_values_should_be_trimmed_set_property() {
    MapSettings settings = new MapSettings(Map.of("foo", "   FOO "));
    assertThat(settings.getString("foo")).isEqualTo("FOO");
  }

  @Test
  public void test_get_default_value() {
    MapSettings settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getDefaultValue("unknown")).isNull();
  }

  @Test
  public void test_get_string() {
    MapSettings settings = new MapSettings(definitions, Map.of("hello", "Russia"));
    assertThat(settings.getString("hello")).isEqualTo("Russia");
  }

  @Test
  public void test_get_date() {
    MapSettings settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getDate("unknown")).isNull();
    assertThat(settings.getDate("date").getDate()).isEqualTo(18);
    assertThat(settings.getDate("date").getMonth()).isEqualTo(4);
  }

  @Test
  public void test_get_date_not_found() {
    MapSettings settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getDate("unknown")).isNull();
  }

  @Test
  public void test_get_datetime() {
    MapSettings settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getDateTime("unknown")).isNull();
    assertThat(settings.getDateTime("datetime").getDate()).isEqualTo(18);
    assertThat(settings.getDateTime("datetime").getMonth()).isEqualTo(4);
    assertThat(settings.getDateTime("datetime").getMinutes()).isEqualTo(50);
  }

  @Test
  public void test_get_double() {
    MapSettings settings = new MapSettings(Map.of("from_string", "3.14159"));
    assertThat(settings.getDouble("from_string")).isEqualTo(3.14159, Offset.offset(0.00001));
    assertThat(settings.getDouble("unknown")).isNull();
  }

  @Test
  public void test_get_float() {
    MapSettings settings = new MapSettings(Map.of("from_string", "3.14159"));
    assertThat(settings.getDouble("from_string")).isEqualTo(3.14159f, Offset.offset(0.00001));
    assertThat(settings.getDouble("unknown")).isNull();
  }

  @Test
  public void test_get_bad_float() {
    MapSettings settings = new MapSettings(Map.of("foo", "bar"));

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("The property 'foo' is not a float value");
    settings.getFloat("foo");
  }

  @Test
  public void test_get_bad_double() {
    MapSettings settings = new MapSettings(Map.of("foo", "bar"));

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("The property 'foo' is not a double value");
    settings.getDouble("foo");
  }

  @Test
  public void getStringArray() {
    MapSettings settings = new MapSettings(definitions, Map.of());
    String[] array = settings.getStringArray("array");
    assertThat(array).isEqualTo(new String[] {"one", "two", "three"});
  }

  @Test
  public void getStringArray_no_value() {
    MapSettings settings = new MapSettings(Map.of());
    String[] array = settings.getStringArray("array");
    assertThat(array).isEmpty();
  }

  @Test
  public void shouldTrimArray() {
    MapSettings settings = new MapSettings(Map.of("foo", "  one,  two, three  "));
    String[] array = settings.getStringArray("foo");
    assertThat(array).isEqualTo(new String[] {"one", "two", "three"});
  }

  @Test
  public void shouldKeepEmptyValuesWhenSplitting() {
    MapSettings settings = new MapSettings(Map.of("foo", "  one,  , two"));
    String[] array = settings.getStringArray("foo");
    assertThat(array).isEqualTo(new String[] {"one", "", "two"});
  }

  @Test
  public void testDefaultValueOfGetString() {
    MapSettings settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getString("hello")).isEqualTo("world");
  }

  @Test
  public void set_property_boolean() {
    MapSettings settings = new MapSettings(Map.of("foo", "true", "bar", "false"));
    assertThat(settings.getBoolean("foo")).isTrue();
    assertThat(settings.getBoolean("bar")).isFalse();
    assertThat(settings.getString("foo")).isEqualTo("true");
    assertThat(settings.getString("bar")).isEqualTo("false");
  }

  @Test
  public void ignore_case_of_boolean_values() {
    MapSettings settings = new MapSettings(Map.of("foo", "true", "bar", "TRUE", "baz", "True"));

    assertThat(settings.getBoolean("foo")).isTrue();
    assertThat(settings.getBoolean("bar")).isTrue();
    assertThat(settings.getBoolean("baz")).isTrue();
  }

  @Test
  public void get_boolean() {
    MapSettings settings = new MapSettings(definitions, Map.of());
    assertThat(settings.getBoolean("boolean")).isTrue();
    assertThat(settings.getBoolean("falseboolean")).isFalse();
    assertThat(settings.getBoolean("unknown")).isFalse();
    assertThat(settings.getBoolean("hello")).isFalse();
  }

  @Test
  public void shouldCreateByIntrospectingComponent() {
    MapSettings settings = new MapSettings(Map.of());
    settings.getDefinitions().addComponent(MyComponent.class);

    // property definition has been loaded, ie for default value
    assertThat(settings.getDefaultValue("foo")).isEqualTo("bar");
  }

  @Property(key = "foo", name = "Foo", defaultValue = "bar")
  public static class MyComponent {

  }

  @Test
  public void getStringLines_no_value() {
    Assertions.assertThat(new MapSettings(Map.of()).getStringLines("foo")).isEmpty();
  }

  @Test
  public void getStringLines_single_line() {
    MapSettings settings = new MapSettings(Map.of("foo", "the line"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"the line"});
  }

  @Test
  public void getStringLines_linux() {
    MapSettings settings = new MapSettings(Map.of("foo", "one\ntwo"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});

    settings = new MapSettings(Map.of("foo", "one\ntwo\n"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});
  }

  @Test
  public void getStringLines_windows() {
    MapSettings settings = new MapSettings(Map.of("foo", "one\r\ntwo"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});

    settings = new MapSettings(Map.of("foo", "one\r\ntwo\r\n"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});
  }

  @Test
  public void getStringLines_mix() {
    MapSettings settings = new MapSettings(Map.of("foo", "one\r\ntwo\nthree"));
    assertThat(settings.getStringLines("foo")).isEqualTo(new String[] {"one", "two", "three"});
  }

  @Test
  public void getKeysStartingWith() {
    MapSettings settings = new MapSettings(Map.of("sonar.jdbc.url", "foo", "sonar.jdbc.username", "bar", "sonar.security", "admin"));

    assertThat(settings.getKeysStartingWith("sonar")).containsOnly("sonar.jdbc.url", "sonar.jdbc.username", "sonar.security");
    assertThat(settings.getKeysStartingWith("sonar.jdbc")).containsOnly("sonar.jdbc.url", "sonar.jdbc.username");
    assertThat(settings.getKeysStartingWith("other")).isEmpty();
  }

  @Test
  public void should_fallback_deprecated_key_to_default_value_of_new_key() {
    MapSettings settings = new MapSettings(definitions, Map.of());

    assertThat(settings.getString("newKeyWithDefaultValue")).isEqualTo("default_value");
    assertThat(settings.getString("oldKeyWithDefaultValue")).isEqualTo("default_value");
  }

  @Test
  public void should_fallback_deprecated_key_to_new_key() {
    MapSettings settings = new MapSettings(definitions, Map.of("newKey", "value of newKey"));

    assertThat(settings.getString("newKey")).isEqualTo("value of newKey");
    assertThat(settings.getString("oldKey")).isEqualTo("value of newKey");
  }

  @Test
  public void should_load_value_of_deprecated_key() {
    // it's used for example when deprecated settings are set through command-line
    MapSettings settings = new MapSettings(definitions, Map.of("oldKey", "value of oldKey"));

    assertThat(settings.getString("newKey")).isEqualTo("value of oldKey");
    assertThat(settings.getString("oldKey")).isEqualTo("value of oldKey");
  }

  @Test
  public void should_load_values_of_deprecated_key() {
    MapSettings settings = new MapSettings(definitions, Map.of("oldKey", "a,b"));

    assertThat(settings.getStringArray("newKey")).containsOnly("a", "b");
    assertThat(settings.getStringArray("oldKey")).containsOnly("a", "b");
  }

  @Test
  public void should_support_deprecated_props_with_multi_values() {
    MapSettings settings = new MapSettings(definitions, Map.of("new_multi_values", " A , B "));
    assertThat(settings.getStringArray("new_multi_values")).isEqualTo(new String[] {"A", "B"});
    assertThat(settings.getStringArray("old_multi_values")).isEqualTo(new String[] {"A", "B"});
  }
}
