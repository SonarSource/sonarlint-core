/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.grip.suggest.parsing;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.grip.web.api.SuggestFixWebApiRequest;
import org.sonarsource.sonarlint.core.grip.web.api.SuggestFixWebApiResponse;

import static org.assertj.core.api.Assertions.assertThat;

class JsonDiffParserTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private final JsonDiffParser parser = new JsonDiffParser();

  @Test
  void it_should_parse_non_consecutive_after_snippet() {
    var diff = parser.parse(new SuggestFixWebApiRequest(null, null, null, null, null, null, "rule:key"),
      new SuggestFixWebApiResponse(UUID.randomUUID(), "{\n" +
        "  \"fix\": [\n" +
        "    {\n" +
        "      \"before\": [\n" +
        "        \"70:     fun updateStatusInlayPanel(status: AiFindingState, issueUuid: UUID, inlayQuickFixPanel: InlayQuickFixPanel) {\",\n" +
        "        \"71:         val inlayData = inlayPerIssueUuid[issueUuid]\",\n" +
        "        \"72:         inlayData?.inlaySnippets?.filter { it.inlayPanel == inlayQuickFixPanel }?.forEach {\",\n" +
        "        \"73:             it.status = status\",\n" +
        "        \"74:         }\",\n" +
        "        \"75:         inlayData?.inlaySnippets?.all { it.status == AiFindingState.ACCEPTED }?.let {\",\n" +
        "        \"76:             if (it) {\",\n" +
        "        \"77:                 inlayData.status = AiFindingState.ACCEPTED\",\n" +
        "        \"78:             }\",\n" +
        "        \"79:         }\",\n" +
        "        \"80:         inlayData?.inlaySnippets?.all { it.status == AiFindingState.DECLINED }?.let {\",\n" +
        "        \"81:             if (it) {\",\n" +
        "        \"82:                 inlayData.status = AiFindingState.DECLINED\",\n" +
        "        \"83:             }\",\n" +
        "        \"84:         }\",\n" +
        "        \"85:         inlayData?.inlaySnippets?.all { it.status == AiFindingState.LOADED }?.let {\",\n" +
        "        \"86:             if (it) {\",\n" +
        "        \"87:                 inlayData.status = AiFindingState.LOADED\",\n" +
        "        \"88:             }\",\n" +
        "        \"89:         }\",\n" +
        "        \"90:         inlayData?.inlaySnippets?.all { it.status == AiFindingState.LOADING }?.let {\",\n" +
        "        \"91:             if (it) {\",\n" +
        "        \"92:                 inlayData.status = AiFindingState.LOADING\",\n" +
        "        \"93:             }\",\n" +
        "        \"94:         }\",\n" +
        "        \"95:         val hasAccepted = inlayData?.inlaySnippets?.any { it.status == AiFindingState.ACCEPTED } ?: false\",\n" +
        "        \"96:         val hasDeclined = inlayData?.inlaySnippets?.any { it.status == AiFindingState.DECLINED } ?: false\",\n" +
        "        \"97:         val allResolved =\",\n" +
        "        \"98:             inlayData?.inlaySnippets?.all { it.status == AiFindingState.ACCEPTED || it.status == AiFindingState.DECLINED } ?: false\",\n" +
        "        \"99:         if (hasAccepted && hasDeclined && allResolved) {\",\n" +
        "        \"100:             inlayData?.status = AiFindingState.PARTIAL\",\n" +
        "        \"101:         }\",\n" +
        "        \"102:         inlayData?.inlaySnippets?.all { it.status == AiFindingState.FAILED }?.let {\",\n" +
        "        \"103:             if (it) {\",\n" +
        "        \"104:                 inlayData.status = AiFindingState.FAILED\",\n" +
        "        \"105:             }\",\n" +
        "        \"106:         }\",\n" +
        "        \"107: \",\n" +
        "        \"108:         if (inlayData != null && (inlayData.status == AiFindingState.ACCEPTED || inlayData.status == AiFindingState.DECLINED || inlayData.status == AiFindingState.PARTIAL)) {\",\n" +
        "        \"109:             runOnUiThread(project) {\",\n" +
        "        \"110:                 getService(\",\n" +
        "        \"111:                     project, SonarLintToolWindow::class.java\",\n" +
        "        \"112:                 ).tryRefreshAiTab(issueUuid)\",\n" +
        "        \"113:             }\",\n" +
        "        \"114:             get(project).simpleNotification(\",\n" +
        "        \"115:                 null,\",\n" +
        "        \"116:                 \\\"The fix has been resolved, please give a feedback!\\\",\",\n" +
        "        \"117:                 ERROR,\",\n" +
        "        \"118:                 object : AnAction(\\\"Submit Feedback\\\") {\",\n" +
        "        \"119:                     override fun actionPerformed(e: AnActionEvent) {\",\n" +
        "        \"120:                         runOnUiThread(project) {\",\n" +
        "        \"121:                             GiveFeedbackDialog(project, issueUuid).show()\",\n" +
        "        \"122:                         }\",\n" +
        "        \"123:                     }\",\n" +
        "        \"124:                 }\",\n" +
        "        \"125:             )\",\n" +
        "        \"126:         }\",\n" +
        "        \"127:     }\"\n" +
        "      ],\n" +
        "      \"after\": [\n" +
        "        \"70:     fun updateStatusInlayPanel(status: AiFindingState, issueUuid: UUID, inlayQuickFixPanel: InlayQuickFixPanel) {\",\n" +
        "        \"71:         val inlayData = inlayPerIssueUuid[issueUuid]\",\n" +
        "        \"72:         updateInlaySnippetsStatus(inlayData, status, inlayQuickFixPanel)\",\n" +
        "        \"73:         updateInlayDataStatus(inlayData)\",\n" +
        "        \"74:         notifyStatusChangeIfNeeded(inlayData, issueUuid)\",\n" +
        "        \"75:     }\",\n" +
        "        \"\",\n" +
        "        \"126:     private fun updateInlaySnippetsStatus(inlayData: InlayData?, status: AiFindingState, inlayQuickFixPanel: InlayQuickFixPanel) {\",\n" +
        "        \"127:         inlayData?.inlaySnippets?.filter { it.inlayPanel == inlayQuickFixPanel }?.forEach {\",\n" +
        "        \"128:             it.status = status\",\n" +
        "        \"129:         }\",\n" +
        "        \"130:     }\",\n" +
        "        \"\",\n" +
        "        \"131:     private fun updateInlayDataStatus(inlayData: InlayData?) {\",\n" +
        "        \"132:         val statuses = AiFindingState.values().filter { state ->\",\n" +
        "        \"133:             inlayData?.inlaySnippets?.all { it.status == state } == true\",\n" +
        "        \"134:         }\",\n" +
        "        \"135:         if (statuses.isNotEmpty()) inlayData?.status = statuses.first()\",\n" +
        "        \"136:         val hasAccepted = inlayData?.inlaySnippets?.any { it.status == AiFindingState.ACCEPTED } ?: false\",\n" +
        "        \"137:         val hasDeclined = inlayData?.inlaySnippets?.any { it.status == AiFindingState.DECLINED } ?: false\",\n" +
        "        \"138:         val allResolved =\",\n" +
        "        \"139:             inlayData?.inlaySnippets?.all { it.status == AiFindingState.ACCEPTED || it.status == AiFindingState.DECLINED } ?: false\",\n" +
        "        \"140:         if (hasAccepted && hasDeclined && allResolved) {\",\n" +
        "        \"141:             inlayData?.status = AiFindingState.PARTIAL\",\n" +
        "        \"142:         }\",\n" +
        "        \"143:     }\",\n" +
        "        \"\",\n" +
        "        \"144:     private fun notifyStatusChangeIfNeeded(inlayData: InlayData?, issueUuid: UUID) {\",\n" +
        "        \"145:         if (inlayData != null && (inlayData.status == AiFindingState.ACCEPTED || inlayData.status == AiFindingState.DECLINED || inlayData.status == AiFindingState.PARTIAL)) {\",\n" +
        "        \"146:             runOnUiThread(project) {\",\n" +
        "        \"147:                 getService(\",\n" +
        "        \"148:                     project, SonarLintToolWindow::class.java\",\n" +
        "        \"149:                 ).tryRefreshAiTab(issueUuid)\",\n" +
        "        \"150:             }\",\n" +
        "        \"151:             get(project).simpleNotification(\",\n" +
        "        \"152:                 null,\",\n" +
        "        \"153:                 \\\"The fix has been resolved, please give a feedback!\\\",\",\n" +
        "        \"154:                 ERROR,\",\n" +
        "        \"155:                 object : AnAction(\\\"Submit Feedback\\\") {\",\n" +
        "        \"156:                     override fun actionPerformed(e: AnActionEvent) {\",\n" +
        "        \"157:                         runOnUiThread(project) {\",\n" +
        "        \"158:                             GiveFeedbackDialog(project, issueUuid).show()\",\n" +
        "        \"159:                         }\",\n" +
        "        \"160:                     }\",\n" +
        "        \"161:                 }\",\n" +
        "        \"162:             )\",\n" +
        "        \"163:         }\",\n" +
        "        \"164:     }\"\n" +
        "      ]\n" +
        "    }\n" +
        "  ],\n" +
        "  \"message\": \"The method `updateStatusInlayPanel` has been refactored to reduce cognitive complexity by extracting parts of its logic into separate methods. This makes the code easier to understand and maintain.\"\n" +
        "}", 0));

    assertThat(diff.isRight()).isTrue();
  }

}
