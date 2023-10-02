/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.newcode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Measures;
import org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverapi.newcode.NewCodeApi.getPeriodForServer;
import static org.sonarsource.sonarlint.core.serverapi.newcode.NewCodeApi.getPeriodFromWs;

class NewCodeApiTest {

  private static final String PROJECT = "project";
  private static final String BRANCH = "branch";
  private static final Version RECENT_SQ_VERSION = Version.create("10.2");
  private static final Version SC_VERSION = Version.create("8.0.0.46314");
  private static final String SOME_DATE = "2023-08-29T09:37:59+0000";
  private static final long SOME_DATE_EPOCH_MILLIS = ServerApiUtils.parseOffsetDateTime(SOME_DATE).toInstant().toEpochMilli();

  private ServerApiHelper mockApiHelper;

  private NewCodeApi underTest;

  @BeforeEach
  void setup() {
    mockApiHelper = mock(ServerApiHelper.class);
    underTest = new NewCodeApi(mockApiHelper);
  }

  @Test
  void getPeriodForNewSonarQube() {
    var response = Measures.ComponentWsResponse
      .newBuilder().setPeriod(Measures.Period.newBuilder()
        .setDate(SOME_DATE).build())
      .build();

    var period = getPeriodFromWs(response);

    assertThat(period.getDate()).isEqualTo(SOME_DATE);
  }

  @Test
  void getPeriodsForOldSonarQubeOrSonarCloud() {
    var response = Measures.ComponentWsResponse
      .newBuilder().setPeriods(Measures.Periods.newBuilder().addPeriods(Measures.Period.newBuilder()
        .setDate(SOME_DATE).build()).build())
      .build();

    var period = getPeriodFromWs(response);

    assertThat(period.getDate()).isEqualTo(SOME_DATE);
  }

  @Test
  void getPeriodFromServer() {
    var serverApiHelper = mock(ServerApiHelper.class);
    when(serverApiHelper.isSonarCloud()).thenReturn(true);

    var sonarCloud = getPeriodForServer(serverApiHelper, Version.create("9.2"));
    when(serverApiHelper.isSonarCloud()).thenReturn(false);
    var sonarQubeOld = getPeriodForServer(serverApiHelper, Version.create("8.0"));
    var sonarQubeNew = getPeriodForServer(serverApiHelper, Version.create("8.1"));

    assertThat(sonarCloud).isEqualTo("periods");
    assertThat(sonarQubeOld).isEqualTo("periods");
    assertThat(sonarQubeNew).isEqualTo("period");
  }

  @Test
  void parseReferenceBranchPeriod() {
    prepareSqWsResponseWithPeriod(Measures.Period.newBuilder()
      .setMode("REFERENCE_BRANCH")
      .setParameter("referenceBranch")
      .build());

    var newCodeDefinition = underTest.getNewCodeDefinition(PROJECT, BRANCH, RECENT_SQ_VERSION);

    assertThat(newCodeDefinition).isInstanceOf(NewCodeDefinition.NewCodeReferenceBranch.class)
      .hasToString("Compared to branch referenceBranch (not supported)");
    assertThat(newCodeDefinition.isOnNewCode(0)).isTrue();
    assertThat(newCodeDefinition.isSupported()).isFalse();

  }

  @Test
  void parseNumberOfDaysPeriodFromSq() {
    prepareSqWsResponseWithPeriod(Measures.Period.newBuilder()
      .setMode("NUMBER_OF_DAYS")
      .setParameter("42")
      .setDate(SOME_DATE)
      .build());

    var newCodeDefinition = underTest.getNewCodeDefinition(PROJECT, BRANCH, RECENT_SQ_VERSION);

    assertThat(newCodeDefinition).isInstanceOf(NewCodeDefinition.NewCodeNumberOfDays.class)
      .hasToString("From last 42 days");
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS + 1)).isTrue();
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS - 1)).isFalse();
    assertThat(newCodeDefinition.isSupported()).isTrue();
  }

  @Test
  void parseNumberOfDaysPeriodFromSc() {
    prepareScWsResponseWithPeriods(Measures.Period.newBuilder()
      .setMode("days")
      .setParameter("42")
      .setDate(SOME_DATE)
      .build());

    var newCodeDefinition = underTest.getNewCodeDefinition(PROJECT, BRANCH, SC_VERSION);

    assertThat(newCodeDefinition).isInstanceOf(NewCodeDefinition.NewCodeNumberOfDays.class)
      .hasToString("From last 42 days");
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS + 1)).isTrue();
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS - 1)).isFalse();
    assertThat(newCodeDefinition.isSupported()).isTrue();
  }

  @Test
  void parsePreviousVersionPeriodFromSq() {
    prepareSqWsResponseWithPeriod(Measures.Period.newBuilder()
      .setMode("PREVIOUS_VERSION")
      .setParameter("version")
      .setDate(SOME_DATE)
      .build());

    var newCodeDefinition = underTest.getNewCodeDefinition(PROJECT, BRANCH, RECENT_SQ_VERSION);

    assertThat(newCodeDefinition).isInstanceOf(NewCodeDefinition.NewCodePreviousVersion.class)
      .hasToString("Since version version");
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS + 1)).isTrue();
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS - 1)).isFalse();
    assertThat(newCodeDefinition.isSupported()).isTrue();
  }

  @Test
  void parsePreviousVersionPeriodFromSc() {
    prepareScWsResponseWithPeriods(Measures.Period.newBuilder()
      .setMode("previous_version")
      .setParameter("version")
      .setDate(SOME_DATE)
      .build());

    var newCodeDefinition = underTest.getNewCodeDefinition(PROJECT, BRANCH, SC_VERSION);

    assertThat(newCodeDefinition).isInstanceOf(NewCodeDefinition.NewCodePreviousVersion.class)
      .hasToString("Since version version");
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS + 1)).isTrue();
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS - 1)).isFalse();
    assertThat(newCodeDefinition.isSupported()).isTrue();
  }

  @Test
  void parseSpecificAnalysisPeriodFromSq() {
    prepareSqWsResponseWithPeriod(Measures.Period.newBuilder()
      .setMode("SPECIFIC_ANALYSIS")
      .setParameter("someAnalysisKey")
      .setDate(SOME_DATE)
      .build());

    var newCodeDefinition = underTest.getNewCodeDefinition(PROJECT, BRANCH, RECENT_SQ_VERSION);

    var date = NewCodeDefinition.formatEpochToDate(SOME_DATE_EPOCH_MILLIS);
    assertThat(newCodeDefinition).isInstanceOf(NewCodeDefinition.NewCodeSpecificAnalysis.class)
      .hasToString("Since analysis from " + date);
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS + 1)).isTrue();
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS - 1)).isFalse();
    assertThat(newCodeDefinition.isSupported()).isTrue();
  }

  @Test
  void parseSpecificVersionPeriodFromSc() {
    prepareScWsResponseWithPeriods(Measures.Period.newBuilder()
      .setMode("version")
      .setParameter("X.Y.Z")
      .setDate(SOME_DATE)
      .build());

    var newCodeDefinition = underTest.getNewCodeDefinition(PROJECT, BRANCH, SC_VERSION);

    var date = NewCodeDefinition.formatEpochToDate(SOME_DATE_EPOCH_MILLIS);
    assertThat(newCodeDefinition).isInstanceOf(NewCodeDefinition.NewCodeSpecificAnalysis.class)
      .hasToString("Since analysis from " + date);
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS + 1)).isTrue();
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS - 1)).isFalse();
    assertThat(newCodeDefinition.isSupported()).isTrue();
  }

  @Test
  void parseSpecificDatePeriodFromSc() {
    prepareScWsResponseWithPeriods(Measures.Period.newBuilder()
      .setMode("date")
      .setDate(SOME_DATE)
      .build());

    var newCodeDefinition = underTest.getNewCodeDefinition(PROJECT, BRANCH, SC_VERSION);

    var date = NewCodeDefinition.formatEpochToDate(SOME_DATE_EPOCH_MILLIS);
    assertThat(newCodeDefinition).isInstanceOf(NewCodeDefinition.NewCodeSpecificAnalysis.class)
      .hasToString("Since analysis from " + date);
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS + 1)).isTrue();
    assertThat(newCodeDefinition.isOnNewCode(SOME_DATE_EPOCH_MILLIS - 1)).isFalse();
    assertThat(newCodeDefinition.isSupported()).isTrue();
  }

  @Test
  void parseUnknownModePeriod() {
    prepareSqWsResponseWithPeriod(Measures.Period.newBuilder()
      .setMode("Definitely not a supported mode")
      .setParameter("Whatever")
      .build());
    assertThrows(IllegalArgumentException.class, () -> underTest.getNewCodeDefinition(PROJECT, BRANCH, RECENT_SQ_VERSION));
  }

  @Test
  void failHttpCall() {
    when(mockApiHelper.get(anyString()))
      .thenThrow(new RuntimeException("Not good"));
    assertThrows(IllegalStateException.class, () -> underTest.getNewCodeDefinition(PROJECT, BRANCH, RECENT_SQ_VERSION));
  }

  void prepareSqWsResponseWithPeriod(Measures.Period period) {
    when(mockApiHelper.isSonarCloud()).thenReturn(false);
    var httpResponse = mock(HttpClient.Response.class);
    when(httpResponse.bodyAsStream()).thenReturn(Measures.ComponentWsResponse.newBuilder()
        .setPeriod(period)
        .build().toByteString().newInput());
    when(mockApiHelper.get("/api/measures/component.protobuf?additionalFields=period&metricKeys=projects&component=" + PROJECT + "&branch=" + BRANCH))
      .thenReturn(httpResponse);
  }

  void prepareScWsResponseWithPeriods(Measures.Period period) {
    when(mockApiHelper.isSonarCloud()).thenReturn(true);
    var httpResponse = mock(HttpClient.Response.class);
    when(httpResponse.bodyAsStream()).thenReturn(Measures.ComponentWsResponse.newBuilder()
      .setPeriods(Measures.Periods.newBuilder()
        .addPeriods(period)
        .build())
      .build().toByteString().newInput());
    when(mockApiHelper.get("/api/measures/component.protobuf?additionalFields=periods&metricKeys=projects&component=" + PROJECT + "&branch=" + BRANCH))
      .thenReturn(httpResponse);
  }
}
