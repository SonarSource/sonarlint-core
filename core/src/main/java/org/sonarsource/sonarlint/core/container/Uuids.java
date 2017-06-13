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
package org.sonarsource.sonarlint.core.container;

public class Uuids {

  public static final String UUID_EXAMPLE_01 = "AU-Tpxb--iU5OvuD2FLy";
  public static final String UUID_EXAMPLE_02 = "AU-TpxcA-iU5OvuD2FLz";
  public static final String UUID_EXAMPLE_03 = "AU-TpxcA-iU5OvuD2FL0";
  public static final String UUID_EXAMPLE_04 = "AU-TpxcA-iU5OvuD2FL1";
  public static final String UUID_EXAMPLE_05 = "AU-TpxcA-iU5OvuD2FL2";
  public static final String UUID_EXAMPLE_06 = "AU-TpxcA-iU5OvuD2FL3";
  public static final String UUID_EXAMPLE_07 = "AU-TpxcA-iU5OvuD2FL4";
  public static final String UUID_EXAMPLE_08 = "AU-TpxcA-iU5OvuD2FL5";
  public static final String UUID_EXAMPLE_09 = "AU-TpxcB-iU5OvuD2FL6";
  public static final String UUID_EXAMPLE_10 = "AU-TpxcB-iU5OvuD2FL7";

  private Uuids() {
    // only static fields
  }

  public static String createFast() {
    return UidFactoryFast.getInstance().create();
  }
}
