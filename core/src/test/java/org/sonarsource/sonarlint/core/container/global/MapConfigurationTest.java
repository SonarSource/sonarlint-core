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

import java.util.Date;
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
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;

public class MapConfigurationTest {
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
  public void setProperty_throws_NPE_if_key_is_null() {
    MapConfiguration underTest = new MapConfiguration();

    expectKeyNullNPE();

    underTest.setProperty(null, randomAlphanumeric(3));
  }

  @Test
  public void setProperty_throws_NPE_if_value_is_null() {
    MapConfiguration underTest = new MapConfiguration();

    expectedException.expect(NullPointerException.class);
    expectedException.expectMessage("value can't be null");

    underTest.setProperty(randomAlphanumeric(3), (String) null);
  }

  @Test
  public void setProperty_accepts_empty_value_and_trims_it() {
    MapConfiguration underTest = new MapConfiguration();
    Random random = new Random();
    String key = randomAlphanumeric(3);

    underTest.setProperty(key, blank(random));

    assertThat(underTest.get(key)).isEmpty();
  }

  @Test
  public void set_property_string_throws_NPE_if_key_is_null() {
    String key = randomAlphanumeric(3);

    MapConfiguration underTest = new MapConfiguration(new PropertyDefinitions(System2.INSTANCE, singletonList(PropertyDefinition.builder(key).multiValues(true).build())));

    expectKeyNullNPE();

    underTest.setProperty(null, new String[] {"1", "2"});
  }

  private void expectKeyNullNPE() {
    expectedException.expect(NullPointerException.class);
    expectedException.expectMessage("key can't be null");
  }

  @Test
  public void set_property_string_array_trims_key() {
    String key = randomAlphanumeric(3);

    MapConfiguration underTest = new MapConfiguration(new PropertyDefinitions(System2.INSTANCE, singletonList(PropertyDefinition.builder(key).multiValues(true).build())));

    Random random = new Random();
    String blankBefore = blank(random);
    String blankAfter = blank(random);

    underTest.setProperty(blankBefore + key + blankAfter, new String[] {"1", "2"});

    assertThat(underTest.hasKey(key)).isTrue();
  }

  private static String blank(Random random) {
    StringBuilder b = new StringBuilder();
    IntStream.range(0, random.nextInt(3)).mapToObj(s -> " ").forEach(b::append);
    return b.toString();
  }

  @Test
  public void setProperty_methods_trims_value() {
    MapConfiguration underTest = new MapConfiguration();

    Random random = new Random();
    String blankBefore = blank(random);
    String blankAfter = blank(random);
    String key = randomAlphanumeric(3);
    String value = randomAlphanumeric(3);

    underTest.setProperty(key, blankBefore + value + blankAfter);

    assertThat(underTest.get(key)).contains(value);
  }

  @Test
  public void set_property_int() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", 123);
    assertThat(configuration.getInt("foo")).isEqualTo(123);
    assertThat(configuration.get("foo")).contains("123");
    assertThat(configuration.getBoolean("foo")).contains(false);
  }

  @Test
  public void default_number_values_are_zero() {
    MapConfiguration settings = new MapConfiguration();
    assertThat(settings.getInt("foo")).contains(0);
    assertThat(settings.getLong("foo")).contains(0L);
  }

  @Test
  public void getInt_value_must_be_valid() {
    thrown.expect(NumberFormatException.class);

    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "not a number");
    configuration.getInt("foo");
  }

  @Test
  public void all_values_should_be_trimmed_set_property() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "   FOO ");
    assertThat(configuration.get("foo")).contains("FOO");
  }

  @Test
  public void test_get_default_value() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    assertThat(configuration.getDefaultValue("unknown")).isNull();
  }

  @Test
  public void test_get_string() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("hello", "Russia");
    assertThat(configuration.get("hello")).contains("Russia");
  }

  @Test
  public void setProperty_date() {
    MapConfiguration configuration = new MapConfiguration();
    Date date = DateUtils.parseDateTime("2010-05-18T15:50:45+0100");
    configuration.setProperty("aDate", date);
    configuration.setProperty("aDateTime", date, true);

    assertThat(configuration.get("aDate")).contains("2010-05-18");
    assertThat(configuration.get("aDateTime").get()).startsWith("2010-05-18T");
  }

  @Test
  public void test_get_double() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("from_double", 3.14159);
    configuration.setProperty("from_string", "3.14159");
    assertThat(configuration.getDouble("from_double").get()).isEqualTo(3.14159, Offset.offset(0.00001));
    assertThat(configuration.getDouble("from_string").get()).isEqualTo(3.14159, Offset.offset(0.00001));
    assertThat(configuration.getDouble("unknown")).isNull();
  }

  @Test
  public void test_get_float() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("from_float", 3.14159f);
    configuration.setProperty("from_string", "3.14159");
    assertThat(configuration.getDouble("from_float").get()).isEqualTo(3.14159f, Offset.offset(0.00001));
    assertThat(configuration.getDouble("from_string").get()).isEqualTo(3.14159f, Offset.offset(0.00001));
    assertThat(configuration.getDouble("unknown")).isNull();
  }

  @Test
  public void test_get_bad_float() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "bar");

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("The property 'foo' is not a float value");
    configuration.getFloat("foo");
  }

  @Test
  public void test_get_bad_double() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "bar");

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("The property 'foo' is not a double value");
    configuration.getDouble("foo");
  }

  @Test
  public void testSetNullFloat() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", (Float) null);
    assertThat(configuration.getFloat("foo")).isNull();
  }

  @Test
  public void testSetNullDouble() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", (Double) null);
    assertThat(configuration.getDouble("foo")).isNull();
  }

  @Test
  public void getStringArray() {
    MapConfiguration mapConfiguration = new MapConfiguration(definitions);
    String[] array = mapConfiguration.getStringArray("array");
    assertThat(array).isEqualTo(new String[] {"one", "two", "three"});
  }

  @Test
  public void setStringArray() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("multi_values", new String[] {"A", "B"});
    String[] array = configuration.getStringArray("multi_values");
    assertThat(array).isEqualTo(new String[] {"A", "B"});
  }

  @Test
  public void setStringArrayTrimValues() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("multi_values", new String[] {" A ", " B "});
    String[] array = configuration.getStringArray("multi_values");
    assertThat(array).isEqualTo(new String[] {"A", "B"});
  }

  @Test
  public void setStringArrayEscapeCommas() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("multi_values", new String[] {"A,B", "C,D"});
    String[] array = configuration.getStringArray("multi_values");
    assertThat(array).isEqualTo(new String[] {"A,B", "C,D"});
  }

  @Test
  public void setStringArrayWithEmptyValues() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("multi_values", new String[] {"A,B", "", "C,D"});
    String[] array = configuration.getStringArray("multi_values");
    assertThat(array).isEqualTo(new String[] {"A,B", "", "C,D"});
  }

  @Test
  public void setStringArrayWithNullValues() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("multi_values", new String[] {"A,B", null, "C,D"});
    String[] array = configuration.getStringArray("multi_values");
    assertThat(array).isEqualTo(new String[] {"A,B", "", "C,D"});
  }

  @Test(expected = IllegalStateException.class)
  public void shouldFailToSetArrayValueOnSingleValueProperty() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("array", new String[] {"A", "B", "C"});
  }

  @Test
  public void getStringArray_no_value() {
    MapConfiguration configuration = new MapConfiguration();
    String[] array = configuration.getStringArray("array");
    assertThat(array).isEmpty();
  }

  @Test
  public void shouldTrimArray() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "  one,  two, three  ");
    String[] array = configuration.getStringArray("foo");
    assertThat(array).isEqualTo(new String[] {"one", "two", "three"});
  }

  @Test
  public void shouldKeepEmptyValuesWhenSplitting() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "  one,  , two");
    String[] array = configuration.getStringArray("foo");
    assertThat(array).isEqualTo(new String[] {"one", "", "two"});
  }

  @Test
  public void testDefaultValueOfGetString() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    assertThat(configuration.get("hello")).contains("world");
  }

  @Test
  public void set_property_boolean() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", true);
    configuration.setProperty("bar", false);
    assertThat(configuration.getBoolean("foo")).contains(true);
    assertThat(configuration.getBoolean("bar")).contains(false);
    assertThat(configuration.get("foo")).contains("true");
    assertThat(configuration.get("bar")).contains("false");
  }

  @Test
  public void ignore_case_of_boolean_values() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "true");
    configuration.setProperty("bar", "TRUE");
    // labels in UI
    configuration.setProperty("baz", "True");

    assertThat(configuration.getBoolean("foo")).contains(true);
    assertThat(configuration.getBoolean("bar")).contains(true);
    assertThat(configuration.getBoolean("baz")).contains(true);
  }

  @Test
  public void get_boolean() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    assertThat(configuration.getBoolean("boolean")).contains(true);
    assertThat(configuration.getBoolean("falseboolean")).contains(false);
    assertThat(configuration.getBoolean("unknown")).contains(false);
    assertThat(configuration.getBoolean("hello")).contains(false);
  }

  @Test
  public void getStringLines_no_value() {
    Assertions.assertThat(new MapConfiguration().getStringLines("foo")).hasSize(0);
  }

  @Test
  public void getStringLines_single_line() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "the line");
    assertThat(configuration.getStringLines("foo")).isEqualTo(new String[] {"the line"});
  }

  @Test
  public void getStringLines_linux() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "one\ntwo");
    assertThat(configuration.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});

    configuration.setProperty("foo", "one\ntwo\n");
    assertThat(configuration.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});
  }

  @Test
  public void getStringLines_windows() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "one\r\ntwo");
    assertThat(configuration.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});

    configuration.setProperty("foo", "one\r\ntwo\r\n");
    assertThat(configuration.getStringLines("foo")).isEqualTo(new String[] {"one", "two"});
  }

  @Test
  public void getStringLines_mix() {
    MapConfiguration configuration = new MapConfiguration();
    configuration.setProperty("foo", "one\r\ntwo\nthree");
    assertThat(configuration.getStringLines("foo")).isEqualTo(new String[] {"one", "two", "three"});
  }

  @Test
  public void should_fallback_deprecated_key_to_default_value_of_new_key() {
    MapConfiguration configuration = new MapConfiguration(definitions);

    assertThat(configuration.get("newKeyWithDefaultValue")).contains("default_value");
    assertThat(configuration.get("oldKeyWithDefaultValue")).contains("default_value");
  }

  @Test
  public void should_fallback_deprecated_key_to_new_key() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("newKey", "value of newKey");

    assertThat(configuration.get("newKey")).contains("value of newKey");
    assertThat(configuration.get("oldKey")).contains("value of newKey");
  }

  @Test
  public void should_load_value_of_deprecated_key() {
    // it's used for example when deprecated settings are set through command-line
    MapConfiguration settings = new MapConfiguration(definitions);
    settings.setProperty("oldKey", "value of oldKey");

    assertThat(settings.get("newKey")).contains("value of oldKey");
    assertThat(settings.get("oldKey")).contains("value of oldKey");
  }

  @Test
  public void should_load_values_of_deprecated_key() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("oldKey", "a,b");

    assertThat(configuration.getStringArray("newKey")).containsOnly("a", "b");
    assertThat(configuration.getStringArray("oldKey")).containsOnly("a", "b");
  }

  @Test
  public void should_support_deprecated_props_with_multi_values() {
    MapConfiguration configuration = new MapConfiguration(definitions);
    configuration.setProperty("new_multi_values", new String[] {" A ", " B "});
    assertThat(configuration.getStringArray("new_multi_values")).isEqualTo(new String[] {"A", "B"});
    assertThat(configuration.getStringArray("old_multi_values")).isEqualTo(new String[] {"A", "B"});
  }
}
