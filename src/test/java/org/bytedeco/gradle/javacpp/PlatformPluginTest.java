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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import org.bytedeco.javacpp.Loader;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlatformPluginTest {
    @Test public void pluginAddsRule() throws IllegalAccessException, InvocationTargetException, NoSuchFieldException {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("org.bytedeco.gradle-javacpp-platform");

        assertEquals(Loader.Detector.getPlatform(), project.findProperty("javacppPlatform"));
        ComponentMetadataHandler h = project.getDependencies().getComponents();
        Field f = DefaultComponentMetadataHandler.class.getDeclaredField("metadataRuleContainer");
        f.setAccessible(true);
        Iterable i = (Iterable)f.get(h);
        assertTrue(i.iterator().hasNext());
    }
}
