#!/usr/bin/env bash

S=';'
CLASSPATH=bin${S}../Eventing/target/EventFramework-0.0.1.jar${S}../ObjectPool/target/ObjectPool-0.0.1.jar${S}../StateMachine/target/StateMachine-0.0.1.jar$XCP

java -Djava.util.logging.config.file=logging.conf -cp $CLASSPATH com.github.glfrazier.snd.simulation.Optimizer $@
