#!/bin/sh
mvn eclipse:clean
mvn -DdownloadSources=true -DdownloadJavadocs=true -DoutputDirectory=target/eclipse-classes -Declipse.workspace=/home/bbossola/projects/gerritforge/workspace eclipse:eclipse eclipse:configure-workspace
