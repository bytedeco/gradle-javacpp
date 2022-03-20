package org.bytedeco.gradle.javacpp;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.spi.ToolProvider;

public class BuildNativeModuleTask extends DefaultTask {

    @Classpath @InputFiles
    FileCollection getRuntimeClasspath() {
        SourceSetContainer ssc = (SourceSetContainer) getProject().getProperties().get("sourceSets");
        return ssc.getByName("main").getRuntimeClasspath();
    }

    @OutputFile
    File getModuleFile() {
        return new File(new File(getProject().getBuildDir(), "native"), "native.jmod");
    }

    @TaskAction
    void impl() throws IOException {
        File buildDir = getProject().getBuildDir();
        File nativeDir = new File(buildDir, "native");
        Path nativePath = nativeDir.toPath();
        Path libPath= nativePath.resolve("lib");
        Path classesPath= nativePath.resolve("classes");
        Path srcPath= nativePath.resolve("java");

        // We should support adding other main classes if the application has several launchers with different main
        // classes
        JavaApplication javaApplication = getProject().getExtensions().findByType(JavaApplication.class);
        if (javaApplication == null)
            throw new GradleException("'application' has not been configured");
        String mainClass = javaApplication.getMainClass().getOrNull();
        if (mainClass == null)
            throw new GradleException("main class has not been set in the 'application' configuration");

        Files.createDirectories(libPath);
        clearDirectory(libPath);
        Files.createDirectories(srcPath);
        clearDirectory(srcPath);
        Files.writeString(srcPath.resolve("module-info.java"), "module org.bytedeco.javacpp.libs {}");

        BytecodeAnalyzer bca = new BytecodeAnalyzer(getRuntimeClasspath(), getProject());

        List<String> deps = bca.getJavaCPPDependencies(mainClass);

        bca.loadClasses(deps, libPath, getLogger());

        ToolProvider javacTool = ToolProvider.findFirst("javac").orElseThrow();
        int res = javacTool.run(System.out, System.err,
            "-d", classesPath.toString(),
            "--release", "11",
            srcPath.resolve("module-info.java").toString());
        if (res != 0) throw new GradleException("Compilation of module-info.java failed");

        File jmodFile = getModuleFile();
        //noinspection ResultOfMethodCallIgnored
        jmodFile.delete();
        ToolProvider jmodTool = ToolProvider.findFirst("jmod").orElseThrow();
        res = jmodTool.run(System.out, System.err,
            "create",
            "--libs", libPath.toString(),
            "--class-path", classesPath.toString(),
            jmodFile.getAbsolutePath()
        );
        if (res != 0) throw new GradleException("Creation of jmod failed");
    }

    private void clearDirectory(Path libPath) throws IOException {
        Files.list(libPath).forEach(p -> {
            if (Files.isDirectory(p))
                throw new GradleException("Unexpected directory " + p);
            try {
                Files.delete(p);
            } catch (IOException e) {
                throw new GradleException("Cannot delete file ", e);
            }
        });
    }

}
