package org.bytedeco.gradle.javacpp;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskInstantiationException;

abstract class VersionSpecific {
  static void registerBuildNativeModuleTask(Project project) {
    throw new TaskInstantiationException("The javacppBuildNativeModule task requires Java 11+");
  }
}