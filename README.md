SonarLint Core
==============
Core library to run SonarLint analysis (used by SonarLint Eclipse and IntelliJ), as well as the SonarLint language server (used by SonarLint VSCode)

[![Travis CI Build Status](https://travis-ci.org/SonarSource/sonarlint-core.svg?branch=master)](https://travis-ci.org/SonarSource/sonarlint-core)
[![AppVeyor Build status](https://ci.appveyor.com/api/projects/status/teulmha62fw3n07h/branch/master?svg=true)](https://ci.appveyor.com/project/SonarSource/sonarlint-core/branch/master)

Have Question or Feedback?
--------------------------

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

Execute from project base directory:

    mvn install

### Run integration tests

    ./run-integration-tests.sh LATEST_RELEASE

License
-------

Copyright 2016-2019 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
