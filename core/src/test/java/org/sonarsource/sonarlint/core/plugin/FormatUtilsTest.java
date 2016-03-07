/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.text.ParseException;
import java.util.Date;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormatUtilsTest {

  @Test
  public void test_to_date() throws ParseException {
    assertThat(FormatUtils.toDate("2010-05-18", false).getDate()).isEqualTo(18);
  }

  @Test
  public void ignore_null_and_empty_date() {
    assertThat(FormatUtils.toDate(null, true)).isNull();
    assertThat(FormatUtils.toDate("", true)).isNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void should_throw_exception_on_invalid_format() {
    assertThat(FormatUtils.toDate("2010", true)).isNull();
  }

  @Test
  public void test_to_string() throws ParseException {
    Date date = DateUtils.parseDate("2010-05-18", new String[] {"yyyy-MM-dd"});
    assertThat(FormatUtils.toString(date, false)).isNotNull();
  }

  @Test
  public void should_return_null__if_no_date() throws ParseException {
    assertThat(FormatUtils.toString(null, false)).isNull();
  }

}
