package org.bytedeco.gradle.javacpp;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.stream.Collectors;

abstract class VersionSpecific {
  static void installNativeModulesComputation(Project project, JavaCPPExtension ext) {

    SourceSetContainer sourceSets = (SourceSetContainer) project.getProperties().get("sourceSets");
    sourceSets.all(sourceSet -> {
      String taskName = sourceSet.getClassesTaskName();
      project
          .getTasks()
          .findByName(taskName)
          .doLast(task -> ext.nativeModulesOf.put(sourceSet.getName(),
              sourceSet
                  .getRuntimeClasspath()
                  .getFiles()
                  .stream()
                  .map(file -> {
                    ModuleDescriptor moduleDescriptor =
                        ModuleFinder.of(file.toPath())
                            .findAll()
                            .stream()
                            .findFirst()
                            .map(ModuleReference::descriptor)
                            .orElse(null);
                    return moduleDescriptor == null ? null : moduleDescriptor.name();
                  })
                  .filter(s -> s != null && ext.platforms.stream().map(platform -> platform.replace('-', '.')).anyMatch(s::contains))
                  .collect(Collectors.joining(","))));
    });
  }
}