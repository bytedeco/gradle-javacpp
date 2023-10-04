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

import groovy.util.Node;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.artifact.FileBasedMavenArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides helper methods intended to be used with the "maven-publish" plugin.
 *
 * @author Samuel Audet
 */
public class BuildExtension {
    private static final Constructor<FileBasedMavenArtifact> compatibleArtifactConstructor;
    private static final boolean isLegacy;
    private final Logger logger = LoggerFactory.getLogger(BuildExtension.class);

    BuildPlugin plugin;
    Project project;

    static {
        boolean legacyCheck;
        Constructor<FileBasedMavenArtifact> compatibleConstructor;
        try {
            // If gradle version is lower than 6.2, use legacy constructor.
            compatibleConstructor = FileBasedMavenArtifact.class.getConstructor(File.class);
            legacyCheck = true;
        } catch (NoSuchMethodException e) {
            try {
                // If gradle version is equals or higher than 6.2, use latest constructor.
                compatibleConstructor = FileBasedMavenArtifact.class.getConstructor(File.class, TaskDependencyFactory.class);
                legacyCheck = false;
            } catch (NoSuchMethodException e2) {
                // If no compatible constructor found, constructor signature modified on latest version and do not compatible with this code.
                // Throw exception to prevent build.
                throw new RuntimeException("Could not find constructor for FileBasedMavenArtifact (Incompatible with this gradle version)", e);
            }
        }
        isLegacy = legacyCheck;
        compatibleArtifactConstructor = compatibleConstructor;
    }

    public BuildExtension(BuildPlugin plugin) {
        this.plugin = plugin;
        this.project = plugin.project;
    }

    /**
     * Copies artifacts that can be resolved from the configuration's dependencies, while
     * excluding the JAR file being built. With this, we can easily republish all JAR files
     * with classifiers at the same time, successfully working around Gradle's limitations.
     *
     * @param configuration containing dependencies to use
     * @return copies of existing artifacts
     * @throws java.io.IOException when JAR files cannot be copied into the project's build libs directory
     * @see <a href="https://github.com/gradle/gradle/issues/2882">https://github.com/gradle/gradle/issues/2882</a>
     */
    public List<MavenArtifact> existingArtifacts(Configuration configuration) throws IOException {
        ArrayList<MavenArtifact> artifacts = new ArrayList<MavenArtifact>();
        BasePluginConvention bc = project.getConvention().getPlugin(BasePluginConvention.class);
        File libsDir = new File(project.getBuildDir(), bc.getLibsDirName());
        libsDir.mkdirs();
        try {
            // Temporarily rename our project to prevent Gradle from resolving the artifacts to project dependencies without files.
            Field nameField = DefaultProject.class.getDeclaredField("name");
            nameField.setAccessible(true);
            String name = (String)nameField.get(project);
            nameField.set(project, name + "-renamed");
            for (ResolvedDependency rd : configuration.getResolvedConfiguration().getLenientConfiguration().getFirstLevelModuleDependencies()) {
                if (rd.getModuleGroup().equals(project.getGroup()) && rd.getModuleName().equals(name)) {
                    for (ResolvedArtifact ra : rd.getModuleArtifacts()) {
                        if (ra.getClassifier() != null && !ra.getClassifier().equals(plugin.getPlatform() + plugin.getPlatformExtension())) {
                            try {
                                File in = ra.getFile();
                                File out = new File(libsDir, in.getName());
                                Files.copy(in.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                MavenArtifact ma = isLegacy ? compatibleArtifactConstructor.newInstance(out) :
                                        compatibleArtifactConstructor.newInstance(out, DefaultTaskDependencyFactory.withNoAssociatedProject());
                                ma.setClassifier(ra.getClassifier());
                                artifacts.add(ma);
                            } catch (RuntimeException e) {
                                // probably ArtifactNotFoundException -> ignore
                            }
                        }
                    }
                }
            }
            nameField.set(project, name);
        } catch (ReflectiveOperationException e) {
            logger.warn("Could not get artifacts: " + e);
        }
        return artifacts;
    }

    /** Returns {@code xmlAction(configuration, null)}. */
    public Action<? extends XmlProvider> xmlAction(Configuration configuration) {
        return xmlAction(configuration, null);
    }
    /**
     * Returns object to be called with {@link MavenPom#withXml(Action)} to create a pom.xml file for "-platform" artifacts.
     *
     * @param configuration containing dependencies to use
     * @param extension like "-gpl", "-gpu", "-python", etc (optional)
     * @return an {@link Action} that fills up an {@link XmlProvider}
     * @see PlatformPlugin
     * @see <a href="https://github.com/bytedeco/javacpp-presets/wiki/Reducing-the-Number-of-Dependencies">Reducing the Number of Dependencies</a>
     */
    public Action<? extends XmlProvider> xmlAction(final Configuration configuration, final String extension) {
        return new Action<XmlProvider>() { public void execute(XmlProvider xml) {
            String[] allPlatforms = {"android-arm", "android-arm64", "android-x86", "android-x86_64",
                                     "ios-arm", "ios-arm64", "ios-x86", "ios-x86_64",
                                     "linux-armhf", "linux-arm64", "linux-ppc64le", "linux-x86", "linux-x86_64",
                                     "macosx-arm64", "macosx-x86_64", "windows-x86", "windows-x86_64"};

            String[] osNameFrom = {"linux", "mac os x", "windows"};
            String[] osNameKernel = {"linux", "darwin", "windows"};
            String[] osNameType = {"name", "name", "family"};
            String[] osNameTo = {"linux", "macosx", "windows"};

            String[] osArchFrom = {"arm", "aarch64", "armv8", "ppc64le", "i386", "i486", "i586", "i686", "amd64", "x86-64"};
            String[] osArchTo = {"armhf", "arm64", "arm64", "ppc64le", "x86", "x86", "x86", "x86", "x86_64", "x86_64"};

            ArrayList<String> platforms = new ArrayList<String>();
            Node propertiesNode = xml.asNode().appendNode("properties");
            Node dependenciesNode = xml.asNode().appendNode("dependencies");
            for (ResolvedDependency rd : configuration.getResolvedConfiguration().getLenientConfiguration().getFirstLevelModuleDependencies()) {
                if (rd.getModuleGroup().equals(project.getGroup()) && rd.getModuleName().equals(project.getName())) {
                    Node dependencyNode = dependenciesNode.appendNode("dependency");
                    dependencyNode.appendNode("groupId", rd.getModuleGroup());
                    dependencyNode.appendNode("artifactId", rd.getModuleName());
                    dependencyNode.appendNode("version", rd.getModuleVersion());
                }
                for (ResolvedArtifact ra : rd.getModuleArtifacts()) {
                    Node dependencyNode = dependenciesNode.appendNode("dependency");
                    dependencyNode.appendNode("groupId", rd.getModuleGroup());
                    dependencyNode.appendNode("artifactId", rd.getModuleName());
                    dependencyNode.appendNode("version", rd.getModuleVersion());
                    if (ra.getClassifier() != null) {
                        String platform = ra.getClassifier();
                        if (extension != null && platform.endsWith(extension)) {
                            platform = platform.substring(0, platform.length() - extension.length());
                        }
                        dependencyNode.appendNode("classifier", "${javacpp.platform." + platform + "}");
                        platforms.add(platform);
                    }
                }
            }

            propertiesNode.appendNode("javacpp.platform.extension", extension != null ? extension : "");
            for (String platform : platforms) {
                propertiesNode.appendNode("javacpp.platform." + platform, platform + "${javacpp.platform.extension}");
            }

            Node profilesNode = xml.asNode().appendNode("profiles");
            Node profileNode = profilesNode.appendNode("profile");
            profileNode.appendNode("id", "javacpp-platform-default");
            profileNode.appendNode("activation").appendNode("property").appendNode("name", "!javacpp.platform");
            propertiesNode = profileNode.appendNode("properties");
            propertiesNode.appendNode("javacpp.platform", "${os.name}-${os.arch}");

            profileNode = profilesNode.appendNode("profile");
            profileNode.appendNode("id", "javacpp-platform-custom");
            profileNode.appendNode("activation").appendNode("property").appendNode("name", "javacpp.platform");
            propertiesNode = profileNode.appendNode("properties");
            for (String profilePlatform : platforms) {
                propertiesNode.appendNode("javacpp.platform." + profilePlatform, "${javacpp.platform}${javacpp.platform.extension}");
            }

            profileNode = profilesNode.appendNode("profile");
            profileNode.appendNode("id", "javacpp-platform-host");
            profileNode.appendNode("activation").appendNode("property").appendNode("name", "javacpp.platform.host");
            propertiesNode = profileNode.appendNode("properties");
            propertiesNode.appendNode("javacpp.platform", "${os.name}-${os.arch}${javacpp.platform.extension}");
            for (String profilePlatform : platforms) {
                propertiesNode.appendNode("javacpp.platform." + profilePlatform, "${os.name}-${os.arch}${javacpp.platform.extension}");
            }

            profileNode = profilesNode.appendNode("profile");
            profileNode.appendNode("id", "javacpp.platform.custom-true");
            profileNode.appendNode("activation").appendNode("property").appendNode("name", "javacpp.platform.custom");
            propertiesNode = profileNode.appendNode("properties");
            propertiesNode.appendNode("javacpp.platform", "");
            for (String profilePlatform : platforms) {
                propertiesNode.appendNode("javacpp.platform." + profilePlatform, "");
            }

            profileNode = profilesNode.appendNode("profile");
            profileNode.appendNode("id", "javacpp-platform-none");
            profileNode.appendNode("activation").appendNode("property").appendNode("name", "javacpp.platform.none");
            propertiesNode = profileNode.appendNode("properties");
            propertiesNode.appendNode("javacpp.platform", "");
            for (String profilePlatform : platforms) {
                propertiesNode.appendNode("javacpp.platform." + profilePlatform, "");
            }

            for (String platform : platforms) {
                profileNode = profilesNode.appendNode("profile");
                profileNode.appendNode("id", "javacpp-platform-" + platform);
                Node activationPropertyNode = profileNode.appendNode("activation").appendNode("property");
                activationPropertyNode.appendNode("name", "javacpp.platform");
                activationPropertyNode.appendNode("value", platform);
                propertiesNode = profileNode.appendNode("properties");
                propertiesNode.appendNode("javacpp.platform", platform);
                for (String profilePlatform : platforms) {
                    propertiesNode.appendNode("javacpp.platform." + profilePlatform,
                            platform == profilePlatform ? "${javacpp.platform}${javacpp.platform.extension}" : "");
                }
            }

            // Profiles to modify the transitive dependencies when picked up from other pom.xml files, for example:
            // mvn -Djavacpp.platform.custom -Djavacpp.platform.host -Djavacpp.platform.linux-x86_64 -Djavacpp.platform.windows-x86_64 ...
            for (String platform : platforms) {
                profileNode = profilesNode.appendNode("profile");
                profileNode.appendNode("id", "javacpp.platform." + platform + "-true");
                profileNode.appendNode("activation").appendNode("property").appendNode("name", "javacpp.platform." + platform);
                profileNode.appendNode("properties").appendNode("javacpp.platform." + platform, platform + "${javacpp.platform.extension}");
            }

            for (int i = 0; i < osNameFrom.length; i++) {
                for (int j = 0; j < osArchFrom.length; j++) {
                    String[] osArchs = osArchFrom[j].equals(osArchTo[j]) || (j + 1 < osArchTo.length && osArchTo[j].equals(osArchTo[j + 1]))
                            ? new String[] { osArchFrom[j] }
                            : new String[] { osArchFrom[j], osArchTo[j] };
                    for (String osArch : osArchs) {
                        String platform = osNameTo[i] + "-" + osArchTo[j];
                        if (platforms.contains(platform)) {
                            profileNode = profilesNode.appendNode("profile");
                            profileNode.appendNode("id", "javacpp.platform.custom-" + osNameTo[i] + "-" + osArch);
                            Node activationNode = profileNode.appendNode("activation");
                            activationNode.appendNode("property").appendNode("name", "javacpp.platform.host");
                            Node osNode = activationNode.appendNode("os");
                            osNode.appendNode(osNameType[i], osNameFrom[i]);
                            osNode.appendNode("arch", osArch);
                            profileNode.appendNode("properties").appendNode("javacpp.platform." + platform, platform + "${javacpp.platform.extension}");
                        }
                    }
                }
            }

            // Profiles to set the default javacpp.platform property: If someone knows a better way to do this, please do let me know!
            for (int i = 0; i < osNameFrom.length; i++) {
                profileNode = profilesNode.appendNode("profile");
                profileNode.appendNode("id", osNameTo[i]);
                profileNode.appendNode("activation").appendNode("os").appendNode(osNameType[i], osNameFrom[i]);
                propertiesNode = profileNode.appendNode("properties");
                propertiesNode.appendNode("os.kernel", osNameKernel[i]);
                propertiesNode.appendNode("os.name", osNameTo[i]);
            }

            for (int i = 0; i < osArchFrom.length; i++) {
                if (!osArchFrom[i].equals(osArchTo[i])) {
                    profileNode = profilesNode.appendNode("profile");
                    profileNode.appendNode("id", osArchFrom[i]);
                    profileNode.appendNode("activation").appendNode("os").appendNode("arch", osArchFrom[i]);
                    profileNode.appendNode("properties").appendNode("os.arch", osArchTo[i]);
                }
            }
        }};
    }
}
