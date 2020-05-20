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
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.WriteProperties;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * This plugin defines the following extra properties:
 * <p><ul>
 * <li>"javacppPlatform", which defaults to {@link Loader.Detector#getPlatform()}, and
 * <li>"javacppParserOutput", which defaults to "$buildDir/generated/sources/javacpp/";
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
 */
public class BuildPlugin implements Plugin<Project> {
    public void apply(Project project) {
        if (!project.hasProperty("javacppPlatform")) {
            project.getExtensions().getExtraProperties().set("javacppPlatform", Loader.Detector.getPlatform());
        }
        if (!project.hasProperty("javacppParserOutput")) {
            project.getExtensions().getExtraProperties().set("javacppParserOutput", new File(project.getBuildDir(), "generated/sources/javacpp/"));
        }

        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            public void execute(JavaPlugin javaPlugin) {
                String platform = (String)project.findProperty("javacppPlatform");
                File parserOutput = (File)project.findProperty("javacppParserOutput");

                JavaPluginConvention jc = project.getConvention().getPlugin(JavaPluginConvention.class);
                SourceSet main = jc.getSourceSets().getByName("main");
                main.getJava().srcDir(parserOutput);
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
                    task.properties = platform;
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
                    task.properties = platform;
                    task.outputDirectory = parserOutput;
                    task.dependsOn("javacppCompileJava");
                });

                project.getTasks().getByName("compileJava").dependsOn("javacppBuildParser");

                project.getTasks().register("javacppBuildCompiler", BuildTask.class, task -> {
                    task.classPath = paths;
                    task.properties = platform;
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
                jarTask.exclude("**/" + platform + "/**");
                jarTask.finalizedBy("javacppJar");

                Jar javacppJarTask = project.getTasks().create("javacppJar", Jar.class, task -> {
                    task.from(main.getOutput());
                    task.setClassifier(platform);
                    task.include("**/" + platform + "/**");
                });

                project.getArtifacts().add("archives", javacppJarTask);
            }
        });
    }
}
