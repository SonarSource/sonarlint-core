/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import java.util.Arrays;

public class Version implements Comparable<Version> {

  private final String name;
  private final String nameWithoutQualifier;
  private final int[] numbers;
  private final String qualifier;

  private Version(String version) {
    this.name = version.trim();
    var qualifierPosition = name.indexOf("-");
    if (qualifierPosition != -1) {
      this.qualifier = name.substring(qualifierPosition + 1);
      this.nameWithoutQualifier = name.substring(0, qualifierPosition);
    } else {
      this.qualifier = "";
      this.nameWithoutQualifier = this.name;
    }
    final var split = this.nameWithoutQualifier.split("\\.");
    numbers = new int[split.length];
    for (var i = 0; i < split.length; i++) {
      numbers[i] = Integer.parseInt(split[i]);
    }
  }

  private Version(String name, String nameWithoutQualifier, int[] numbers, String qualifier) {
    this.name = name;
    this.nameWithoutQualifier = nameWithoutQualifier;
    this.numbers = Arrays.copyOf(numbers, numbers.length);
    this.qualifier = qualifier;
  }

  public int getMajor() {
    return numbers.length > 0 ? numbers[0] : 0;
  }

  public int getMinor() {
    return numbers.length > 1 ? numbers[1] : 0;
  }

  public int getPatch() {
    return numbers.length > 2 ? numbers[2] : 0;
  }

  public int getBuild() {
    return numbers.length > 3 ? numbers[3] : 0;
  }

  public String getName() {
    return name;
  }

  public String getQualifier() {
    return qualifier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Version)) {
      return false;
    }
    var other = (Version) o;
    return getMajor() == other.getMajor()
      && getMinor() == other.getMinor()
      && getPatch() == other.getPatch()
      && getBuild() == other.getBuild()
      && qualifier.equals(other.qualifier);
  }

  @Override
  public int hashCode() {
    var result = Integer.hashCode(getMajor());
    result = 31 * result + Integer.hashCode(getMinor());
    result = 31 * result + Integer.hashCode(getPatch());
    result = 31 * result + Integer.hashCode(getBuild());
    result = 31 * result + qualifier.hashCode();
    return result;
  }

  @Override
  public int compareTo(Version other) {
    var c = compareToIgnoreQualifier(other);
    if (c == 0) {
      if ("".equals(qualifier)) {
        c = "".equals(other.qualifier) ? 0 : 1;
      } else if ("".equals(other.qualifier)) {
        c = -1;
      } else {
        c = qualifier.compareTo(other.qualifier);
      }
    }
    return c;
  }

  public int compareToIgnoreQualifier(Version other) {
    var maxNumbers = Math.max(numbers.length, other.numbers.length);
    var myNumbers = Arrays.copyOf(numbers, maxNumbers);
    var otherNumbers = Arrays.copyOf(other.numbers, maxNumbers);
    for (var i = 0; i < maxNumbers; i++) {
      var compare = Integer.compare(myNumbers[i], otherNumbers[i]);
      if (compare != 0) {
        return compare;
      }
    }
    return 0;
  }

  @Override
  public String toString() {
    return name;
  }

  public static Version create(String version) {
    return new Version(version);
  }

  public Version removeQualifier() {
    return new Version(nameWithoutQualifier, nameWithoutQualifier, numbers, "");
  }

  public boolean satisfiesMinRequirement(Version minRequirement) {
    return this.compareToIgnoreQualifier(minRequirement) >= 0;
  }
}
