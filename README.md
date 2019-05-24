# jenkins-plugin

The official Meterian plugin for Jenkins.

This has not been published yet, but you can have a look at the code and snoop around. This plugin is expected to work in classic jobs and pipelines (Jenkinsfile) with the ability to include the analysis as part of your build process, as it should be.

The integration that we are building first, also in light that nobody did that before, is with [Gerrit Code Review](https://www.gerritcodereview.com/), where we with the help of [Robot Comments](https://www.gerritcodereview.com/config-robot-comments.html) we can leverage the automatic fixing ability of Meterian with the smooth review flow of Gerrit.

More to be written here after the plugin is launched :)


## Build

Build the project with the below command from inside the project root folder:

```bash
./bin/build.sh
```

Note: this runs the unit/integration tests which is part of the project and can take a bit of time.

Tests it runs:

Running InjectedTest:

- InjectedTest
- SimpleFileCompareTest
- ClientDownloaderTest

Look in the `target` folder for all the generate artifacts, you will find `meterian-plugin.jar` among others. 

## Run

Run the project with the below command from inside the project root folder:

```bash
./bin/run.sh
```

This kicks off a full-fledged Jenkins instance and can be access via http://0.0.0.0:8080/jenkins/ or http://localhost:8080/jenkins/ or http://127.0.0.1:8080/jenkins/.

One or more jobs can be executed via this console. When these jobs are executed, and depending on the type of job, the Meterian Jenkins plugin configured with each of the jobs is triggered and the respective actions are executed.

## Run Tests 

### All tests

```bash
mvn test
```

### Specific tests

```bash
mvn test -Dtest=MeterianClientRunnerTest
```

## Jenkins job types

### SimplePipeline

Builds a single branch using the Pipeline facility via the `jenkinsfile` script.

Technical detail: flows through the `MeterianPlugin` class and is handled by `SimpleOrFreeStyleExecutor`.

### MultiPipeline

Builds multiple branches concurrently, using the Pipeline facility via the `jenkinsfile` script.

Technical detail: flows through the `MeterianStep` class and is handled by `StandardExecutor`.

### FreeStyle

Builds a single branch but does not give the facility to specific Jenkins config via the `jenkinsfile`.

Technical detail: flows through the `MeterianPlugin` class and is handled by `SimpleOrFreeStyleExecutor`.

## Meterian features

The below features can be used across the above Jenkins job types. 

### Only report

Default behaviour no client args required.

This option does the default task of scanning the project and reporting the vulnerabilities, if present, and recommended fixes but does not apply them.

### Autofix (report and create Pull Request)

Using the `--autofix` client arg option (Go to Jenkins > Configure > Meterian for the field Client JVM args).

This option does the task of scanning the project and reporting the vulnerabilities, if present, and also applying the fix to the respective branch and creating a pull request to the repository.

Ensure your GitHub OAuth token to your Organisation and Repo has been added to the Meterian configuration settings under Jenkins > Configure > Meterian. Enter your GitHub OAuth token in the field **GitHub OA UTH token**. If this field is empty or incorrect appropriate error messages are displayed in the Jenkins logger (console).

#### Running Meterian client from CLI

The below command should do it, provided the plugin has already downloaded the client:

```bash
java -jar ${HOME}/.meterian/meterian-cli.jar [meterian args]
```

[meterian args] - one or more args for additional features i.e. `--interactive=false` or `--autofix`

On the first run, it will authorise you, so please ensure you have an account on https://www.meterian.com.

_Note: the Meterian client is automatically downloaded by the plugin when it detects the absence of it and is saved in the `${HOME}/.meterian` folder._

#### Additional information and sources about writing plugins for Jenkins

- https://wiki.jenkins.io/display/JENKINS/Plugin+tutorial
- https://wiki.jenkins.io/display/JENKINS/Create+a+new+Plugin+with+a+custom+build+Step

See https://stackoverflow.com/questions/43690435/failure-to-find-org-jenkins-ci-pluginspluginpom2-11-in-https-repo-maven-apa on how to avoid the below warning message:

```bash
[WARNING] The POM for org.jenkins-ci.tools:maven-hpi-plugin:jar:2.7 is missing, no dependency information available
[WARNING] Failed to build parent project for io.jenkins.plugins:meterian-plugin:hpi:0.1-SNAPSHOT
```

#### Build status
[![CircleCI](https://circleci.com/gh/MeterianHQ/jenkins-plugin.svg?style=svg)](https://circleci.com/gh/MeterianHQ/jenkins-plugin)

