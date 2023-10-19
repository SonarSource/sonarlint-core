SonarLint Core
==============
Core library to run SonarLint analysis (used by SonarLint Eclipse and IntelliJ), as well as the SonarLint language server (used by SonarLint VSCode). SonarLint helps you deliver [Clean Code](https://www.sonarsource.com/solutions/clean-code/?utm_medium=referral&utm_source=github&utm_campaign=clean-code&utm_content=sonarlint-core) in your IDE.

[![Build Status](https://api.cirrus-ci.com/github/SonarSource/sonarlint-core.svg)](https://cirrus-ci.com/github/SonarSource/sonarlint-core)
[![Quality Gate Status](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.sonarlint.core%3Asonarlint-core-parent)

Have Questions or Feedback?
---------------------------

For SonarLint support questions ("How do I?", "I got this error, why?", ...), please first read the [FAQ](https://community.sonarsource.com/t/frequently-asked-questions/7204) and then head to the [SonarSource forum](https://community.sonarsource.com/c/help/sl). There are chances that a question similar to yours has already been answered. 

Be aware that this forum is a community, so the standard pleasantries ("Hi", "Thanks", ...) are expected. And if you don't get an answer to your thread, you should sit on your hands for at least three days before bumping it. Operators are not standing by. :-)


Contributing
------------

If you would like to see a new feature, please create a new thread in the forum ["Suggest new features"](https://community.sonarsource.com/c/suggestions/features).

Please be aware that we are not actively looking for feature contributions. The truth is that it's extremely difficult for someone outside SonarSource to comply with our roadmap and expectations. Therefore, we typically only accept minor cosmetic changes and typo fixes.

With that in mind, if you would like to submit a code contribution, please create a pull request for this repository. Please explain your motives to contribute this change: what problem you are trying to fix, what improvement you are trying to make.

Make sure that you follow our [code style](https://github.com/SonarSource/sonar-developer-toolset#code-style) and all tests are passing (Travis build is executed for each pull request).

Building
--------

To build sources locally follow these instructions.

### Build and Run Unit Tests

#### Prerequisites

Some medium tests load plugins relying on Node.js, so make sure the latest LTS version is installed and `node` is in the PATH.

Execute from the project base directory:

    mvn verify

### Run integration tests

See [Running Integration Tests](its/README.md)

Documentation
-------------

Have a look at the documentation [here](spec/README.adoc).

License
-------

Copyright 2016-2023 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
