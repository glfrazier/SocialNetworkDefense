#!/usr/bin/env bash

if (( $# == 0 )); then
   echo You must specify properties on the command line.
   echo Suggestion: $0 snd.properties_file=properties/1x1.props
   echo
   echo Remember that all property names are in the form '"snd.*"'.
   echo
   echo You can add to the classpath via the '"XCP"' environment variable.
   echo Make sure that the value you give to XCP begins with your "platform's"
   echo path separator "(':' on Linux, ';' on Windows)", so that it can be
   echo appended to the classpath.
   echo
   exit -1
fi
S=';'
CLASSPATH=bin${S}../Eventing/target/EventFramework-0.0.1.jar${S}../ObjectPool/target/ObjectPool-0.0.1.jar${S}../StateMachine/target/StateMachine-0.0.1.jar$XCP
java -cp $CLASSPATH com.github.glfrazier.snd.simulation.Simulation $@
