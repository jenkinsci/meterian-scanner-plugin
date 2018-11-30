# jenkins-plugin
The official Meterian plugin for Jenkins.

This has not been published yet, but you can have a look at the code and snoop around. This plugin is expected to work in classic jobs and pipelines (Jenkinsfile) with the ability to include the analysis as part of your build process, as it should be.

The integration that we are building first, also in light that nobody did that before, is with [Gerrit Code Review](https://www.gerritcodereview.com/), where we with the help of [Robot Comments](https://www.gerritcodereview.com/config-robot-comments.html) we can leverage the automatic fixing ability of Meterian with the smooth review flow of Gerrit.

More to be written here after the plugin is launched :)

# additional information and sources about writing plugins for Jenkins
https://wiki.jenkins.io/display/JENKINS/Plugin+tutorial
https://wiki.jenkins.io/display/JENKINS/Create+a+new+Plugin+with+a+custom+build+Step
