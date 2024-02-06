/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Measures;

import static org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils.parseOffsetDateTime;

public class NewCodeApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String GET_NEW_CODE_DEFINITION_URL = "/api/measures/component.protobuf";
  private static final String OLD_SQ_OR_SC_PERIOD = "periods";
  private static final String NEW_SQ_PERIOD = "period";
  private static final Version NEW_SQ_VERSION = Version.create("8.1");
  private final ServerApiHelper helper;

  public NewCodeApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Optional<NewCodeDefinition> getNewCodeDefinition(String projectKey, @Nullable String branch, Version serverVersion, SonarLintCancelMonitor cancelMonitor) {
    Measures.ComponentWsResponse response;
    var period = getPeriodForServer(helper, serverVersion);
    var requestPath = new StringBuilder().append(GET_NEW_CODE_DEFINITION_URL)
      .append("?additionalFields=")
      .append(period)
      .append("&metricKeys=projects&component=")
      .append(UrlUtils.urlEncode(projectKey));
    if (branch != null) {
      requestPath.append("&branch=").append(UrlUtils.urlEncode(branch));
    }
    try (
      var wsResponse = helper.get(requestPath.toString(), cancelMonitor);
      var is = wsResponse.bodyAsStream()) {
      response = Measures.ComponentWsResponse.parseFrom(is);
    } catch (Exception e) {
      LOG.error("Error while fetching new code definition", e);
      return Optional.empty();
    }
    var periodFromWs = getPeriodFromWs(response);
    var modeString = periodFromWs.getMode();
    var parameter = periodFromWs.hasParameter() ? periodFromWs.getParameter() : null;
    if (modeString.equals("REFERENCE_BRANCH") && parameter != null) {
      return Optional.of(NewCodeDefinition.withReferenceBranch(parameter));
    }
    var date = periodFromWs.hasDate() ? parseOffsetDateTime(periodFromWs.getDate()).toInstant().toEpochMilli() : 0;
    if ((modeString.equals("NUMBER_OF_DAYS") || modeString.equals("days")) && parameter != null) {
      var days = Integer.parseInt(parameter);
      return Optional.of(NewCodeDefinition.withNumberOfDays(days, date));
    }
    if (modeString.equalsIgnoreCase("PREVIOUS_VERSION")) {
      return Optional.of(NewCodeDefinition.withPreviousVersion(date, parameter));
    }
    if (modeString.equals("SPECIFIC_ANALYSIS") || modeString.equals("version") || modeString.equals("date")) {
      return Optional.of(NewCodeDefinition.withSpecificAnalysis(date));
    }
    LOG.warn("Unsupported mode of new code definition: " + modeString);
    return Optional.empty();
  }

  static Measures.Period getPeriodFromWs(Measures.ComponentWsResponse response) {
    if (response.hasPeriods()) {
      return response.getPeriods().getPeriods(0);
    }
    return response.getPeriod();
  }

  static String getPeriodForServer(ServerApiHelper helper, Version serverVersion) {
    if (helper.isSonarCloud()) {
      return OLD_SQ_OR_SC_PERIOD;
    }
    if (serverVersion.compareToIgnoreQualifier(NEW_SQ_VERSION) < 0) {
      return OLD_SQ_OR_SC_PERIOD;
    }
    return NEW_SQ_PERIOD;
  }

}
