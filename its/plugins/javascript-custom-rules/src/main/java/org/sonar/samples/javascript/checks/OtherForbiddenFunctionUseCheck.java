/*
 * JavaScript Custom Rules Plugin
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonar.samples.javascript.checks;

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Set;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.javascript.api.tree.Tree;
import org.sonar.plugins.javascript.api.tree.Tree.Kind;
import org.sonar.plugins.javascript.api.tree.expression.CallExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.ExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.IdentifierTree;
import org.sonar.plugins.javascript.api.visitors.SubscriptionVisitorCheck;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;
import org.sonarsource.api.sonarlint.SonarLintSide;

/**
 * Example of a check extending {@link SubscriptionVisitorCheck}.
 * 
 * We define the kinds of the nodes that we subscribe to in {@link #nodesToVisit()}.
 * We can then override visitNode or leaveNode: these methods will be called for all nodes
 * of the kinds we subscribed to.
 */
@Rule(
  key = "S2",
  priority = Priority.MINOR,
  name = "'baz' function should not be used.",
  tags = {"convention"},
  description = "It's better not to use the 'baz' function."
  )
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.DATA_RELIABILITY)
@SqaleConstantRemediation("5min")
@SonarLintSide
public class OtherForbiddenFunctionUseCheck extends SubscriptionVisitorCheck {

  private static final Set<String> FORBIDDEN_FUNCTIONS = ImmutableSet.of("baz");

  @Override
  public Set<Kind> nodesToVisit() {
    return Collections.singleton(Kind.CALL_EXPRESSION);
  }

  @Override
  public void visitNode(Tree tree) {
    // we can do this cast because we subscribed only to nodes of kind CALL_EXPRESSION
    CallExpressionTree callExpressionTree = (CallExpressionTree) tree;
    ExpressionTree callee = callExpressionTree.callee();
    if (callee.is(Kind.IDENTIFIER_REFERENCE) && FORBIDDEN_FUNCTIONS.contains(((IdentifierTree) callee).name())) {
      addIssue(tree, "Remove the usage of this forbidden function.");
    }
  }

}
