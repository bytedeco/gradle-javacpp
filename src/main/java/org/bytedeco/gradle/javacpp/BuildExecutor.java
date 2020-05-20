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
import java.util.List;
import java.util.Map;
import org.bytedeco.javacpp.tools.CommandExecutor;
import org.bytedeco.javacpp.tools.Logger;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.internal.DefaultExecAction;

/**
 * Overrides use of ProcessBuilder with something that works within Gradle.
 *
 * @author Samuel Audet
 */
public class BuildExecutor extends CommandExecutor {
    public BuildExecutor(Logger logger) {
        super(logger);
    }

    @Override public int executeCommand(List<String> command, File workingDirectory,
            Map<String, String> environmentVariables) throws IOException, InterruptedException {
        PathToFileResolver resolver = new PathToFileResolver() {
            @Override public File resolve(Object path) { return (File)path; }
            @Override public PathToFileResolver newResolver(File baseDir) { return this; }
            @Override public boolean canResolveRelativePath() { return true; }
        };
        ManagedExecutor executor = new DefaultExecutorFactory().create("BuildExecutor");
        try {
            DefaultExecAction action = new DefaultExecAction(resolver, executor, new DefaultBuildCancellationToken());
            action.setCommandLine(command);
            if (workingDirectory != null) {
                action.workingDir(workingDirectory);
            } else {
                try {
                    action.workingDir(new File(".").getCanonicalFile());
                } catch (IOException ex) {
                    action.workingDir(new File(".").getAbsoluteFile());
                }
            }
            if (environmentVariables != null) {
                for (Map.Entry<String,String> e : environmentVariables.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        action.environment(e.getKey(), e.getValue());
                    }
                }
            }
            return action.execute().getExitValue();
        } finally {
            executor.shutdownNow();
        }
    }
}
