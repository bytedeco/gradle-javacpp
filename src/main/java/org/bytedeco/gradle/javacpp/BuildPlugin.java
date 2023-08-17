/*
 * Copyright (C) 2020-2022 Samuel Audet
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
import java.util.Properties;
import java.util.Set;
import org.bytedeco.javacpp.Loader;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.WriteProperties;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * This plugin creates new packages containing native libraries using JavaCPP.
 * It defines the following extra property:
 * <p><ul>
 * <li>"javacppPlatform", which defaults to {@link Loader#getPlatform()},
 * <li>"javacppPlatformExtension", which defaults to an empty string,
 * </ul><p>
 *
 * creates the following extension:
 * <p><ul>
 * <li>"javacppBuild", an instance of {@link BuildExtension},
 * </ul><p>
 *
 * as well as the following configuration:
 * <p><ul>
 * <li>"javacppPlatform", to be used to specify dependencies for the "-platform" artifact,
 * </ul><p>
 *
 * and registers the following tasks:
 * <p><ul>
 * <li>"javacppBuildCommand" to execute {@link BuildTask#buildCommand},
 * <li>"javacppCompileJava" to compile classes needed by the parser,
 * <li>"javacppBuildParser" to run the parser on these classes,
 * <li>"javacppBuildCompiler" to generate and compile JNI code,
 * <li>"javacppPomProperties" to write version information to pom.properties,
 * <li>"javacppJar" to archive the native libraries in a separate JAR file,
 * <li>"javacppPlatformJar", to create an empty JAR file for the main "-platform" artifact,
 * <li>"javacppPlatformJavadocJar", to create an empty JAR file for the "-platform" javadoc artifact, and
 * <li>"javacppPlatformSourcesJar", to create an empty JAR file for the "-platform" sources artifact,
 * </ul><p>
 *
 * @author Samuel Audet
 */
public class BuildPlugin implements Plugin<Project> {
    Project project;

    String getPlatform() {
        return (String)project.findProperty("javacppPlatform");
    }

    String getPlatformExtension() {
        return (String)project.findProperty("javacppPlatformExtension");
    }

    boolean isLibraryPath(String path) {
        String p = (String)project.findProperty("javacpp.platform.library.path");
        return p != null && p.length() > 0 ? path.startsWith(p) : path.contains("/" + getPlatform() + getPlatformExtension() + "/");
    }

    @Override public void apply(final Project project) {
        this.project = project;
        if (!project.hasProperty("javacppPlatform")) {
            project.getExtensions().getExtraProperties().set("javacppPlatform", Loader.Detector.getPlatform());
        }
        if (!project.hasProperty("javacppPlatformExtension")) {
            project.getExtensions().getExtraProperties().set("javacppPlatformExtension", "");
        }
        if (project.getExtensions().findByName("javacppBuild") == null) {
            project.getExtensions().create("javacppBuild", BuildExtension.class, this);
        }

        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() { public void execute(JavaPlugin javaPlugin) {
            final JavaPluginConvention jc = project.getConvention().getPlugin(JavaPluginConvention.class);
            final SourceSet main = jc.getSourceSets().getByName("main");
            final Set<File> files = main.getOutput().getClassesDirs().getFiles();
            final String[] paths = new String[files.size()];
            int n = 0;
            for (File file : files) {
                try {
                    paths[n++] = file.getCanonicalPath();
                } catch (IOException ex) {
                    paths[n++] = file.getAbsolutePath();
                }
            }

            project.getTasks().register("javacppBuildCommand",
                    BuildTask.class, new Action<BuildTask>() { public void execute(BuildTask task) {
                task.classPath = paths;
                task.properties = getPlatform();
                if (getPlatformExtension() != null && getPlatformExtension().length() > 0) {
                    task.propertyKeysAndValues = new Properties();
                    task.propertyKeysAndValues.setProperty("platform.extension", getPlatformExtension());
                }
                task.classOrPackageNames = new String[0];
                task.workingDirectory = project.getProjectDir();
            }});

            project.getTasks().register("javacppCompileJava",
                    JavaCompile.class, new Action<JavaCompile>() { public void execute(JavaCompile task) {
                task.setSource(main.getJava());
                task.setClasspath(main.getCompileClasspath());
                task.getDestinationDirectory().set(main.getJava().getClassesDirectory());
                task.dependsOn("javacppBuildCommand");
            }});

            project.getTasks().register("javacppBuildParser",
                    BuildTask.class, new Action<BuildTask>() { public void execute(final BuildTask task) {
                task.classPath = paths;
                task.properties = getPlatform();
                if (getPlatformExtension() != null && getPlatformExtension().length() > 0) {
                    task.propertyKeysAndValues = new Properties();
                    task.propertyKeysAndValues.setProperty("platform.extension", getPlatformExtension());
                }
                if (task.outputDirectory == null) {
                    task.outputDirectory = main.getJava().getSrcDirs().iterator().next();
                }
                task.dependsOn("javacppCompileJava");
                task.doFirst(new Action<Task>() { public void execute(Task t) { main.getJava().srcDir(task.outputDirectory); }});
            }});

            project.getTasks().getByName("compileJava").dependsOn("javacppBuildParser");

            project.getTasks().register("javacppBuildCompiler",
                    BuildTask.class, new Action<BuildTask>() { public void execute(BuildTask task) {
                task.classPath = paths;
                task.properties = getPlatform();
                if (getPlatformExtension() != null && getPlatformExtension().length() > 0) {
                    task.propertyKeysAndValues = new Properties();
                    task.propertyKeysAndValues.setProperty("platform.extension", getPlatformExtension());
                }
                task.dependsOn("compileJava");
            }});

            project.getTasks().getByName("classes").dependsOn("javacppBuildCompiler");

            project.getTasks().register("javacppPomProperties",
                    WriteProperties.class, new Action<WriteProperties>() { public void execute(WriteProperties task) {
                Object group = project.findProperty("group");
                Object name = project.findProperty("name");
                Object version = project.findProperty("version");
                task.property("groupId", group);
                task.property("artifactId", name);
                task.property("version", version);
                task.setOutputFile(new File(main.getOutput().getResourcesDir(), "META-INF/maven/" + group + "/" + name + "/pom.properties"));
            }});

            Jar jarTask = (Jar)project.getTasks().getByName("jar");
            jarTask.dependsOn("javacppPomProperties");
            jarTask.exclude(new Spec<FileTreeElement>() { public boolean isSatisfiedBy(FileTreeElement file) {
                return isLibraryPath(file.getPath());
            }});

            TaskProvider<Jar> javacppJarTask = project.getTasks().register("javacppJar",
                    Jar.class, new Action<Jar>() { public void execute(Jar task) {
                task.from(main.getOutput());
                task.getArchiveClassifier().set(getPlatform() + getPlatformExtension());
                task.include(new Spec<FileTreeElement>() { public boolean isSatisfiedBy(FileTreeElement file) {
                    return file.isDirectory() || isLibraryPath(file.getPath());
                }});
                task.dependsOn("jar");
            }});

            project.getArtifacts().add("archives", javacppJarTask);

            TaskProvider<Jar> javacppPlatformJarTask = project.getTasks().register("javacppPlatformJar",
                    Jar.class, new Action<Jar>() { public void execute(Jar task) {
                task.getArchiveBaseName().set(project.getName() + "-platform");
                task.dependsOn("javacppJar");
            }});

            TaskProvider<Jar> javacppPlatformJavadocJarTask = project.getTasks().register("javacppPlatformJavadocJar",
                    Jar.class, new Action<Jar>() { public void execute(Jar task) {
                task.getArchiveBaseName().set(project.getName() + "-platform");
                task.getArchiveClassifier().set("javadoc");
                task.dependsOn("javacppPlatformJar");
            }});

            TaskProvider<Jar> javacppPlatformSourcesTask = project.getTasks().register("javacppPlatformSourcesJar",
                    Jar.class, new Action<Jar>() { public void execute(Jar task) {
                task.getArchiveBaseName().set(project.getName() + "-platform");
                task.getArchiveClassifier().set("sources");
                task.dependsOn("javacppPlatformJar");
            }});

            project.getConfigurations().maybeCreate("javacppPlatform");
            project.getArtifacts().add("javacppPlatform", javacppPlatformJarTask);
            project.getArtifacts().add("javacppPlatform", javacppPlatformJavadocJarTask);
            project.getArtifacts().add("javacppPlatform", javacppPlatformSourcesTask);
        }});
    }
}
