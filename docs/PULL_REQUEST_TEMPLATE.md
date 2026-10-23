
# For SonarSourcers:

- [ ] Prefix the commit message with the ticket number, i.e. `SLCORE-XXXX` if you already have a ticket in Jira
- [ ] For standalone PRs without issue in Jira:
    - [ ] Mention Epic ID in this descrition to create a new Task in Jira
    - [ ] Mention Issue ID in this descrition to create a new Sub-Task in Jira
    - [ ] Do not mention any Jira issue to create a new Task in Jira without a parent
- [ ] When changing an API:
    - [ ] Explain in the JavaDoc the purpose of the new API
    - [ ] Document the change in [API_CHANGES.md](https://github.com/SonarSource/sonarlint-core/blob/master/API_CHANGES.md)
    - [ ] If the change breaks the current API, explicitly communicate those to the impacted consumers prior to merging (eg. IDE squad)
- [ ] Make sure the tests adhere to the convention:
    - [ ] All test method names should use `snake_case`, for example: `test_validate_input`.
- [ ] Make sure checks are green: build passes, Quality Gate is green

# For external contributors:

In addition to the above, please review our [contribution guidelines](https://github.com/SonarSource/sonarlint-core/blob/master/docs/contributing.md) and ensure your pull request adheres to the following guidelines:

- [ ] Please explain your motives to contribute this change: what problem you are trying to fix, what improvement you are trying to make
- [ ] Use the following formatting style: [SonarSource/sonar-developer-toolset](https://github.com/SonarSource/sonar-developer-toolset#code-style)
- [ ] Provide a unit test for any code you changed
