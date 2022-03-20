package org.bytedeco.gradle.javacpp;

import javassist.*;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

class BytecodeAnalyzer {

    private final FileCollection runtimePath;
    private final ClassPool cp;
    private final String platform;

    BytecodeAnalyzer(FileCollection runtimePath, Project project) {
        this.runtimePath = runtimePath;

        cp = new ClassPool();

        try {
            cp.appendPathList(runtimePath.getAsPath());
        } catch (NotFoundException e) {
            throw new GradleException("Path component not found", e);
        }

        platform = (String) project.findProperty("javacppPlatform");
        if (platform == null || platform.indexOf(',') >= 0)
            throw new GradleException("The javacppPlatform must contain a single platform to build the native module");
    }

    List<String> getJavaCPPDependencies(String main) {

        HashSet<String> dependencies = new HashSet<>();
        HashSet<String> toAnalyze = new HashSet<>();
        ArrayList<String> javacppClasses = new ArrayList<>();
        toAnalyze.add(main);

        while (!toAnalyze.isEmpty()) {
            HashSet<String> thisDeps = new HashSet<>();
            for (String c : toAnalyze) {
                try {
                    CtClass cc = cp.get(c);
                    if (cc.isFrozen()) continue;
                    ClassFile cf = cc.getClassFile();
                    // Can we check for something more specific ?
                    if (cc.hasAnnotation("org.bytedeco.javacpp.annotation.Properties"))
                        javacppClasses.add(c);

                    ConstPool constPool = cf.getConstPool();
                    for (int ix = 1, size = constPool.getSize(); ix < size; ix++) {
                        int descriptorIndex;
                        switch (constPool.getTag(ix)) {
                            case ConstPool.CONST_Class:
                                thisDeps.add(constPool.getClassInfo(ix));
                            default:
                                continue;
                            case ConstPool.CONST_NameAndType:
                                descriptorIndex = constPool.getNameAndTypeDescriptor(ix);
                                break;
                            case ConstPool.CONST_MethodType:
                                descriptorIndex = constPool.getMethodTypeInfo(ix);
                        }
                        String desc = constPool.getUtf8Info(descriptorIndex);
                        for (int p = 0; p < desc.length(); p++)
                            if (desc.charAt(p) == 'L')
                                thisDeps.add(desc.substring(++p, p = desc.indexOf(';', p)).replace('/', '.'));
                    }
                } catch (NotFoundException ignored) {
                    // java.*, javax.*
                }
            }

            toAnalyze.clear();

            for (String c : thisDeps) {
                if (!dependencies.contains(c)) {
                    dependencies.add(c);
                    toAnalyze.add(c);
                }
            }
        }

        return javacppClasses;
    }

    void loadClasses(List<String> classes, Path cacheDir, Logger logger) {
        System.setProperty("javacpp.platform", platform);
        System.setProperty("org.bytedeco.javacpp.cachedir.nosubdir", "true");
        System.setProperty("org.bytedeco.javacpp.cachedir", cacheDir.toString());

        ArrayList<URL> urls = new ArrayList<>();
        for (File jar : runtimePath) {
            try {
                urls.add(jar.toURI().toURL());
            } catch (MalformedURLException e) {
                logger.warn("Malformed path component", e);
            }
        }
        URLClassLoader resourceLoader = new URLClassLoader(urls.toArray(new URL[0]));

        // Javassist will load classes and delegate resource loading to resourceLoader
        javassist.Loader jLoader = new javassist.Loader(resourceLoader, cp);

        try {
            Class<?> javaCPPLoader = jLoader.loadClass("org.bytedeco.javacpp.Loader");
            Method load = javaCPPLoader.getMethod("load", Class[].class);

            for (String cn : classes) {
                try {
                    Class<?> c = jLoader.loadClass(cn);
                    load.invoke(javaCPPLoader, new Object[]{new Class[]{c}});
                } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException ex) {
                    logger.warn("Cannot load "+cn, ex);
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new GradleException("Cannot load JavaCPP Loader", e);
        }
    }
}
