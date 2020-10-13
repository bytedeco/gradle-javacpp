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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.internal.artifacts.repositories.resolver.AbstractDependencyMetadataAdapter;
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor;
import org.gradle.internal.component.model.IvyArtifactName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A rule that looks at dependencies of artifacts containing "-platform" in their names, and
 * removes any dependency whose classifier doesn't start with values in the "javacppPlatform" property,
 * which allows matches against platform extensions such as "-gpu" without specifying them.
 *
 * @author Samuel Audet
 */
class PlatformRule implements ComponentMetadataRule {
    private final Logger logger = LoggerFactory.getLogger(PlatformRule.class);

    final List<String> platform;

    /** Takes a comma-separated list of platform names to keep. */
    @Inject public PlatformRule(String platform) {
        this.platform = Arrays.asList(platform.split(","));
    }

    @Override public void execute(ComponentMetadataContext context) {
        ComponentMetadataDetails component = context.getDetails();
        if (!component.getId().getName().contains("-platform")) {
            return;
        }
        component.allVariants(variant -> {
            variant.withDependencies(dependencies -> {
                Iterator<DirectDependencyMetadata> i = dependencies.iterator();
                while (i.hasNext()) {
                    DirectDependencyMetadata d = i.next();
                    String classifier = null;
// This only works starting with Gradle 6.3:
//                    List<DependencyArtifact> as = d.getArtifactSelectors();
//                    if (as != null && as.size() > 0) {
//                        classifier = as.get(0).getClassifier();
//                    }
// So try to get the classifier some other way...
                    try {
                        if (d instanceof AbstractDependencyMetadataAdapter) {
                            Method getMetadata = AbstractDependencyMetadataAdapter.class.getDeclaredMethod("getOriginalMetadata");
                            getMetadata.setAccessible(true);
                            Object o = getMetadata.invoke(d);
                            if (o instanceof ConfigurationBoundExternalDependencyMetadata) {
                                ConfigurationBoundExternalDependencyMetadata m = (ConfigurationBoundExternalDependencyMetadata)o;
                                ExternalDependencyDescriptor dd = m.getDependencyDescriptor();
                                if (dd instanceof MavenDependencyDescriptor) {
                                    MavenDependencyDescriptor mdd = (MavenDependencyDescriptor)dd;
                                    IvyArtifactName da = mdd.getDependencyArtifact();
                                    if (da != null) {
                                        classifier = da.getClassifier();
                                    }
                                }
                            }
                        }
                    } catch (ReflectiveOperationException e) {
                        logger.warn("Could not get the classifier of " + d + ": " + e);
                    }
                    String c = classifier;
                    if (classifier != null && platform.stream().filter(p -> c.startsWith(p)).count() == 0) {
                        i.remove();
                    }
                }
            });
        });
    }
}
