/*
 * Copyright (C) 2020 Samuel Audet
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE.txt file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bytedeco.gradle.javacpp;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.bytedeco.javacpp.Loader;
import org.gradle.api.Project;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.WriteProperties;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * This plugin creates new packages containing native libraries using JavaCPP.
 * It defines the following extra property:
 * <p><ul>
 * <li>"javacppPlatform", which defaults to {@link Loader#getPlatform()}, and
 * </ul><p>
 *
 * and registers the following tasks:
 * <p><ul>
 * <li>"javacppBuildCommand" to execute {@link BuildTask#buildCommand},
 * <li>"javacppCompileJava" to compile classes needed by the parser,
 * <li>"javacppBuildParser" to run the parser on these classes,
 * <li>"javacppBuildCompiler" to generate and compile JNI code,
 * <li>"javacppPomProperties" to write version information to pom.properties, and
 * <li>"javacppJar" to archive the native libraries in a separate JAR file.
 * </ul><p>
 *
 * @author Samuel Audet
 */
public class BuildPlugin implements Plugin<Project> {
    Project project;

    String getPlatform() {
        return (String)project.findProperty("javacppPlatform");
    }

    boolean isLibraryPath(String path) {
        String p = (String)project.findProperty("javacpp.platform.library.path");
        return p != null && p.length() > 0 ? path.startsWith(p) : path.contains("/" + getPlatform() + "/");
    }

    @Override public void apply(Project project) {
        this.project = project;
        if (!project.hasProperty("javacppPlatform")) {
            project.getExtensions().getExtraProperties().set("javacppPlatform", Loader.Detector.getPlatform());
        }

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            JavaPluginConvention jc = project.getConvention().getPlugin(JavaPluginConvention.class);
            SourceSet main = jc.getSourceSets().getByName("main");
            Set<File> files = main.getOutput().getClassesDirs().getFiles();
            String[] paths = new String[files.size()];
            int n = 0;
            for (File file : files) {
                try {
                    paths[n++] = file.getCanonicalPath();
                } catch (IOException ex) {
                    paths[n++] = file.getAbsolutePath();
                }
            }

            project.getTasks().register("javacppBuildCommand", BuildTask.class, task -> {
                task.classPath = paths;
                task.properties = getPlatform();
                task.classOrPackageNames = new String[0];
            });

            project.getTasks().register("javacppCompileJava", JavaCompile.class, task -> {
                task.setSource(main.getJava());
                task.setClasspath(main.getCompileClasspath());
                task.setDestinationDir(main.getJava().getOutputDir());
                task.dependsOn("javacppBuildCommand");
            });

            project.getTasks().register("javacppBuildParser", BuildTask.class, task -> {
                task.classPath = paths;
                task.properties = getPlatform();
                task.outputDirectory = main.getJava().getSrcDirs().iterator().next();
                task.dependsOn("javacppCompileJava");
                task.doFirst(t -> { main.getJava().srcDir(task.outputDirectory); });
            });

            project.getTasks().getByName("compileJava").dependsOn("javacppBuildParser");

            project.getTasks().register("javacppBuildCompiler", BuildTask.class, task -> {
                task.classPath = paths;
                task.properties = getPlatform();
                task.dependsOn("compileJava");
            });

            project.getTasks().getByName("classes").dependsOn("javacppBuildCompiler");

            project.getTasks().register("javacppPomProperties", WriteProperties.class, task -> {
                Object group = project.findProperty("group");
                Object name = project.findProperty("name");
                Object version = project.findProperty("version");
                task.property("groupId", group);
                task.property("artifactId", name);
                task.property("version", version);
                task.setOutputFile(new File(main.getOutput().getResourcesDir(), "META-INF/maven/" + group + "/" + name + "/pom.properties"));
            });

            Jar jarTask = (Jar)project.getTasks().getByName("jar");
            jarTask.dependsOn("javacppPomProperties");
            jarTask.exclude(file -> isLibraryPath(file.getPath()));

            TaskProvider<Jar> javacppJarTask = project.getTasks().register("javacppJar", Jar.class, task -> {
                task.from(main.getOutput());
                task.setClassifier(getPlatform());
                task.include(file -> file.isDirectory() || isLibraryPath(file.getPath()));
                task.dependsOn("jar");
            });

            project.getArtifacts().add("archives", javacppJarTask);
        });
    }
}
