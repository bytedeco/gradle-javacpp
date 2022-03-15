package org.bytedeco.gradle.javacpp;

import org.bytedeco.javacpp.Loader;
import org.gradle.api.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class JavaCPPExtension {

    public List<String> platforms;

    /**
     * Comma-separated list of native modules for each source set.
     */
    public HashMap<String, String> nativeModulesOf = new HashMap<>();

    public JavaCPPExtension(Project project) {
        // Initialize platforms with javacppPlatform extra properties for backward compatibility.
        Object javacppPlatform = project.findProperty("javacppPlatform");
        platforms = new ArrayList<>();
        if (javacppPlatform == null) {
            platforms.add(Loader.Detector.getPlatform());
        } else {
            platforms.addAll(Arrays.asList(javacppPlatform.toString().split("\\s*,\\s*")));
        }
    }

    public String getNativeModules() { return nativeModulesOf.get("main"); }
}
