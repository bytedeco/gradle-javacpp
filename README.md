Gradle JavaCPP
==============

[![Gitter](https://badges.gitter.im/bytedeco/javacpp.svg)](https://gitter.im/bytedeco/javacpp) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.bytedeco/gradle-javacpp/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.bytedeco/gradle-javacpp) [![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/https/oss.sonatype.org/org.bytedeco/gradle-javacpp.svg)](http://bytedeco.org/builds/) [![Build Status](https://travis-ci.org/bytedeco/gradle-javacpp.svg?branch=master)](https://travis-ci.org/bytedeco/gradle-javacpp) <sup>Commercial support:</sup> [![xscode](https://img.shields.io/badge/Available%20on-xs%3Acode-blue?style=?style=plastic&logo=appveyor&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAMAAACdt4HsAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAAZQTFRF////////VXz1bAAAAAJ0Uk5T/wDltzBKAAAAlUlEQVR42uzXSwqAMAwE0Mn9L+3Ggtgkk35QwcnSJo9S+yGwM9DCooCbgn4YrJ4CIPUcQF7/XSBbx2TEz4sAZ2q1RAECBAiYBlCtvwN+KiYAlG7UDGj59MViT9hOwEqAhYCtAsUZvL6I6W8c2wcbd+LIWSCHSTeSAAECngN4xxIDSK9f4B9t377Wd7H5Nt7/Xz8eAgwAvesLRjYYPuUAAAAASUVORK5CYII=)](https://xscode.com/bytedeco/gradle-javacpp)


Introduction
------------
Gradle JavaCPP offers plugins that make it easy to use [JavaCPP](https://github.com/bytedeco/javacpp) and [JavaCV](https://github.com/bytedeco/javacv) as part of the Gradle build system.

Please feel free to ask questions on [the mailing list](http://groups.google.com/group/javacpp-project) if you encounter any problems with the software! I am sure it is far from perfect...


Required Software
-----------------
To use Gradle JavaCPP, you will need to download and install the following software:

 * An implementation of Java SE 8 or newer:
   * OpenJDK  http://openjdk.java.net/install/  or
   * Oracle JDK  http://www.oracle.com/technetwork/java/javase/downloads/  or
   * IBM JDK  http://www.ibm.com/developerworks/java/jdk/
 * Gradle 5.0 or newer: https://gradle.org/releases/


Getting Started
---------------
Gradle JavaCPP comes with 2 plugins:

 * [The build plugin](#the-build-plugin) to create new packages containing native libraries using JavaCPP, and
 * [The platform plugin](#the-platform-plugin) to select from existing artifacts the ones corresponding to user-specified platforms.

Fully functional sample projects are also provided in the [`samples`](samples) subdirectory and can be used as templates.


### The Build Plugin
To understand how [JavaCPP](https://github.com/bytedeco/javacpp) is meant to be used, one should first take a look at the [Mapping Recipes for C/C++ Libraries](https://github.com/bytedeco/javacpp/wiki/Mapping-Recipes), but a high-level overview of the [Basic Architecture](https://github.com/bytedeco/javacpp/wiki/Basic-Architecture) is also available to understand the bigger picture.

Once comfortable enough with the command line interface, the build plugin for Gradle can be used to integrate easily that workflow as part of `build.gradle` as shown below. By default, for Java libraries and applications, it creates a `javacppJar` task that archives the native libraries into a separate JAR file and sets its classifier to `$javacppPlatform`, while excluding those files from the default `jar` task. To customize the behavior, there are properties that can be modified and whose documentation is available as part of the source code in these files:

 * [`BuildTask.java`](src/main/java/org/bytedeco/gradle/javacpp/BuildTask.java)
 * [`BuildPlugin.java`](src/main/java/org/bytedeco/gradle/javacpp/BuildPlugin.java)
 * [`BuildExtension.java`](src/main/java/org/bytedeco/gradle/javacpp/BuildExtension.java)

```groovy
plugins {
    id 'java-library'
    id 'org.bytedeco.gradle-javacpp-build' version "$javacppVersion"
}

// We can set this on the command line too this way: -PjavacppPlatform=android-arm64
ext {
    javacppPlatform = 'android-arm64' // or any other platform, defaults to Loader.getPlatform()
}

dependencies {
    api "org.bytedeco:javacpp:$javacppVersion"
}

tasks.withType(org.bytedeco.gradle.javacpp.BuildTask) {
    // set here default values for all build tasks below, typically just includePath and linkPath,
    // but also properties to set the path to the NDK and its compiler in the case of Android
}

javacppBuildCommand {
    // typically set here the buildCommand to the script that fills up includePath and linkPath
}

javacppBuildParser {
    // typically set here the classOrPackageNames to class names implementing InfoMap
}

javacppBuildCompiler {
    // typically set here boolean flags like copyLibs
}
```


#### Integration with Android Studio

It is also possible to integrate the `BuildTask` with Android Studio for projects with C/C++ support by:

 1. Following the instructions at https://developer.android.com/studio/projects/add-native-code ,
 2. Adding something like below to the `app/build.gradle` file, and
```groovy
android.applicationVariants.all { variant ->
    def variantName = variant.name.capitalize() // either "Debug" or "Release"
    def javaCompile = project.tasks.getByName("compile${variantName}JavaWithJavac")
    def generateJson = project.tasks.getByName("generateJsonModel$variantName")

    // Compiles NativeLibraryConfig.java
    task "javacppCompileJava$variantName"(type: JavaCompile) {
        include 'com/example/myapplication/NativeLibraryConfig.java'
        source = javaCompile.source
        classpath = javaCompile.classpath
        destinationDir = javaCompile.destinationDir
    }

    // Parses NativeLibrary.h and outputs NativeLibrary.java
    task "javacppBuildParser$variantName"(type: org.bytedeco.gradle.javacpp.BuildTask) {
        dependsOn "javacppCompileJava$variantName"
        classPath = [javaCompile.destinationDir]
        includePath =  ["$projectDir/src/main/cpp/"]
        classOrPackageNames = ['com.example.myapplication.NativeLibraryConfig']
        outputDirectory = file("$projectDir/src/main/java/")
    }

    // Compiles NativeLibrary.java and everything else
    javaCompile.dependsOn "javacppBuildParser$variantName"

    // Generates jnijavacpp.cpp and jniNativeLibrary.cpp
    task "javacppBuildCompiler$variantName"(type: org.bytedeco.gradle.javacpp.BuildTask) {
        dependsOn javaCompile
        classPath = [javaCompile.destinationDir]
        classOrPackageNames = ['com.example.myapplication.NativeLibrary']
        compile = false
        deleteJniFiles = false
        outputDirectory = file("$projectDir/src/main/cpp/")
    }

    // Picks up the C++ files listed in CMakeLists.txt
    generateJson.dependsOn "javacppBuildCompiler$variantName"
}
```
 3. Updating the `CMakeLists.txt` file to include the generated `.cpp` files.


### The Platform Plugin
With Maven, we are able to modify dependencies transitively using profiles, and although Gradle doesn't provide such functionality out of the box, it can be emulated via plugins. After adding a single line to the `build.gradle` script as shown below, the platform plugin will filter the dependencies of artifacts whose names contain "-platform" using the comma-separated values given in `$javacppPlatform`. To understand better how this works, it may be worth taking a look at the source code of the plugin:

 * [`PlatformRule.java`](src/main/java/org/bytedeco/gradle/javacpp/PlatformRule.java)
 * [`PlatformPlugin.java`](src/main/java/org/bytedeco/gradle/javacpp/PlatformPlugin.java)

```groovy
plugins {
    id 'java-library'
    id 'org.bytedeco.gradle-javacpp-platform' version "$javacppVersion"
}

// We can set this on the command line too this way: -PjavacppPlatform=linux-x86_64,macosx-x86_64,windows-x86_64,etc
ext {
    javacppPlatform = 'linux-x86_64,macosx-x86_64,windows-x86_64,etc' // defaults to Loader.getPlatform()
}

dependencies {
    api "org.bytedeco:javacv-platform:$javacvVersion" // or any other "-platform" artifacts
}
```


----
Project lead: Samuel Audet [samuel.audet `at` gmail.com](mailto:samuel.audet&nbsp;at&nbsp;gmail.com)  
Developer site: https://github.com/bytedeco/gradle-javacpp  
Discussion group: http://groups.google.com/group/javacpp-project
