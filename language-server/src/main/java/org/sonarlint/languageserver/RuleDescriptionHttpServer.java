/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

class RuleDescriptionHttpServer extends NanoHTTPD {

  private final StandaloneSonarLintEngine engine;
  private final String css;

  RuleDescriptionHttpServer(int port, StandaloneSonarLintEngine engine) {
    super(port);
    this.engine = engine;
    try {
      this.css = IOUtils.toString(this.getClass().getResourceAsStream("/rules.css"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Response serve(IHTTPSession session) {
    String ruleKey = session.getParms().get("ruleKey");
    if (ruleKey == null) {
      return newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing 'ruleKey' parameter");
    } else {
      RuleDetails ruleDetails;
      try {
        ruleDetails = engine.getRuleDetails(ruleKey);
      } catch (IllegalArgumentException e) {
        return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "No rules with key '" + ruleKey + "'");
      }
      try {
        String ruleName = ruleDetails.getName();
        String htmlDescription = ruleDetails.getHtmlDescription();
        String type = ruleDetails.getType().toLowerCase(Locale.ENGLISH);
        String typeImg64 = getAsBase64("/images/type/" + type + ".png");
        String severity = ruleDetails.getSeverity().toLowerCase(Locale.ENGLISH);
        String severityImg64 = getAsBase64("/images/severity/" + severity + ".png");
        return newFixedLengthResponse("<!doctype html><html><head><style type=\"text/css\">" + css + "</style></head><body><h1><big>"
          + ruleName + "</big> (" + ruleKey
          + ")</h1>"
          + "<div>"
          + "<img style=\"padding-bottom: 1px;vertical-align: middle\" width=\"16\" height=\"16\" alt=\"" + type + "\" src=\"data:image/gif;base64," + typeImg64 + "\">&nbsp;"
          + clean(type)
          + "&nbsp;"
          + "<img style=\"padding-bottom: 1px;vertical-align: middle\" width=\"16\" height=\"16\" alt=\"" + severity + "\" src=\"data:image/gif;base64," + severityImg64
          + "\">&nbsp;"
          + clean(severity)
          + "</div>"
          + "<div class=\"rule-desc\">" + htmlDescription
          + "</div></body></html>");
      } catch (Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, e.getMessage() + "\n" + sw.toString());
      }
    }
  }

  private static String getAsBase64(String image) throws IOException {
    InputStream resourceAsStream = ServerMain.class.getResourceAsStream(image);
    if (resourceAsStream == null) {
      throw new IllegalStateException("Unable to load image " + image);
    }
    return Base64.getEncoder().encodeToString(IOUtils.toByteArray(resourceAsStream));
  }

  private static String clean(String txt) {
    return StringUtils.capitalize(txt.toLowerCase(Locale.ENGLISH).replace("_", " "));
  }

}
