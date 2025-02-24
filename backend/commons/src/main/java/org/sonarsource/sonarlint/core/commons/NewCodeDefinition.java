/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public interface NewCodeDefinition {

  String DATETIME_FORMAT = "MM/dd/yyyy HH:mm:ss";
  DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_FORMAT);

  NewCodeMode getMode();

  boolean isOnNewCode(long creationDate);

  default boolean isOnNewCode(Instant introductionDate) {
    return isOnNewCode(introductionDate.toEpochMilli());
  }

  boolean isSupported();

  static String formatEpochToDate(long epoch) {
    return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault()).format(DATETIME_FORMATTER);
  }

  static NewCodeDefinition withAlwaysNew() {
    return new NewCodeAlwaysNew();
  }

  static NewCodeDefinition withExactNumberOfDays(int days) {
    return new NewCodeExactNumberOfDays(days);
  }

  /**
   * @param days the theoretical number of days
   * @param thresholdDate the actual date in the past that serves for the comparison. Can be different from the number of days as it is updated after analysis on the server side
   */
  static NewCodeDefinition withNumberOfDaysWithDate(int days, long thresholdDate) {
    return new NewCodeNumberOfDaysWithDate(days, thresholdDate);
  }

  static NewCodeDefinition withPreviousVersion(long thresholdDate, @Nullable String version) {
    return new NewCodePreviousVersion(thresholdDate, version);
  }

  static NewCodeDefinition withReferenceBranch(String referenceBranch) {
    return new NewCodeReferenceBranch(referenceBranch);
  }

  static NewCodeDefinition withSpecificAnalysis(long thresholdDate) {
    return new NewCodeSpecificAnalysis(thresholdDate);
  }

  Instant getThresholdDate();

  abstract class NewCodeDefinitionWithDate implements NewCodeDefinition {
    protected final long thresholdDate;

    protected NewCodeDefinitionWithDate(long thresholdDate) {
      this.thresholdDate = thresholdDate;
    }

    public boolean isOnNewCode(long creationDate) {
      return creationDate > thresholdDate;
    }

    public boolean isSupported() {
      return true;
    }

    public Instant getThresholdDate() {
      return Instant.ofEpochMilli(thresholdDate);
    }
  }

  class NewCodeExactNumberOfDays implements NewCodeDefinition {
    private final int days;

    public NewCodeExactNumberOfDays(int days) {
      this.days = days;
    }

    @Override
    public NewCodeMode getMode() {
      return NewCodeMode.NUMBER_OF_DAYS;
    }

    @Override
    public boolean isOnNewCode(long creationDate) {
      return creationDate > Instant.now().minus(days, ChronoUnit.DAYS).toEpochMilli();
    }

    @Override
    public boolean isSupported() {
      return true;
    }

    @Override
    public Instant getThresholdDate() {
      return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    // Text used by IDEs in the UI. Communicate changes to IDE squad prior to changing the wording.
    @Override
    public String toString() {
      return String.format("From last %s days", days);
    }
  }

  class NewCodeNumberOfDaysWithDate extends NewCodeDefinitionWithDate {
    Integer days;

    private NewCodeNumberOfDaysWithDate(Integer days, long thresholdDate) {
      super(thresholdDate);
      this.days = days;
    }

    // Text used by IDEs in the UI. Communicate changes to IDE squad prior to changing the wording.
    @Override
    public String toString() {
      return String.format("From last %s days", days);
    }

    @Override
    public NewCodeMode getMode() {
      return NewCodeMode.NUMBER_OF_DAYS;
    }

    public Integer getDays() {
      return days;
    }
  }

  class NewCodePreviousVersion extends NewCodeDefinitionWithDate {
    private final String version;

    private NewCodePreviousVersion(long thresholdDate, @Nullable String version) {
      super(thresholdDate);
      this.version = version;
    }

    // Text used by IDEs in the UI. Communicate changes to IDE squad prior to changing the wording.
    @Override
    public String toString() {
      var versionQualifier = (version == null) ? formatEpochToDate(this.thresholdDate) : ("version " + version);
      return String.format("Since %s", versionQualifier);
    }

    @Override
    public NewCodeMode getMode() {
      return NewCodeMode.PREVIOUS_VERSION;
    }

    @CheckForNull
    public String getVersion() {
      return version;
    }
  }

  class NewCodeSpecificAnalysis extends NewCodeDefinitionWithDate {
    private NewCodeSpecificAnalysis(long thresholdDate) {
      super(thresholdDate);
    }

    // Text used by IDEs in the UI. Communicate changes to IDE squad prior to changing the wording.
    @Override
    public String toString() {
      return String.format("Since analysis from %s", formatEpochToDate(this.thresholdDate));
    }

    @Override
    public NewCodeMode getMode() {
      return NewCodeMode.SPECIFIC_ANALYSIS;
    }
  }

  class NewCodeReferenceBranch implements NewCodeDefinition {
    private final String branchName;

    private NewCodeReferenceBranch(String branchName) {
      this.branchName = branchName;
    }

    @Override
    public NewCodeMode getMode() {
      return NewCodeMode.REFERENCE_BRANCH;
    }

    @Override
    public boolean isOnNewCode(long creationDate) {
      return true;
    }

    @Override
    public boolean isSupported() {
      return false;
    }

    public String getBranchName() {
      return branchName;
    }

    @Override
    public Instant getThresholdDate() {
      // instead of Long.MAX_VALUE it's set for Instant.now() in case it will be used for git blame limit
      return Instant.now();
    }

    // Text used by IDEs in the UI. Communicate changes to IDE squad prior to changing the wording.
    @Override
    public String toString() {
      return "Current new code definition (reference branch) is not supported";
    }
  }

  class NewCodeAlwaysNew implements NewCodeDefinition {

    private NewCodeAlwaysNew() {
      // NOP
    }

    @Override
    public NewCodeMode getMode() {
      throw new UnsupportedOperationException("Mode shouldn't be called for this new code definition");
    }

    @Override
    public boolean isOnNewCode(long creationDate) {
      return true;
    }

    @Override
    public Instant getThresholdDate() {
      // instead of 0L it's set for Instant.now() in case it will be used for git blame limit (which shouldn't normally happen)
      return Instant.now();
    }

    @Override
    public boolean isSupported() {
      return true;
    }
  }
}
