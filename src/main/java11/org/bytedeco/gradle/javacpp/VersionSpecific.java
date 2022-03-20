package org.bytedeco.gradle.javacpp;

import org.gradle.api.Project;

abstract class VersionSpecific {
  static void registerBuildNativeModuleTask(Project project) {
    project.getTasks().register("javacppBuildNativeModule", BuildNativeModuleTask.class);
  }
}