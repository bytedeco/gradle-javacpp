Gradle JavaCPP
==============

[![Gitter](https://badges.gitter.im/bytedeco/javacpp.svg)](https://gitter.im/bytedeco/javacpp) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.bytedeco/gradle-javacpp/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.bytedeco/gradle-javacpp) [![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/https/oss.sonatype.org/org.bytedeco/gradle-javacpp.svg)](http://bytedeco.org/builds/) [![Build Status](https://travis-ci.org/bytedeco/gradle-javacpp.svg?branch=master)](https://travis-ci.org/bytedeco/gradle-javacpp)


Introduction
------------
Gradle JavaCPP offers plugins that make it easy to use [JavaCPP](https://github.com/bytedeco/javacpp) as part of the Gradle build system.

Please feel free to ask questions on [the mailing list](http://groups.google.com/group/javacpp-project) if you encounter any problems with the software! I am sure it is far from perfect...


Required Software
-----------------
To use Gradle JavaCPP, you will need to download and install the following software:

 * An implementation of Java SE 8 or newer:
   * OpenJDK  http://openjdk.java.net/install/  or
   * Oracle JDK  http://www.oracle.com/technetwork/java/javase/downloads/  or
   * IBM JDK  http://www.ibm.com/developerworks/java/jdk/
 * A C++ compiler, out of which these have been tested:
   * GNU C/C++ Compiler (Linux, etc.)  http://gcc.gnu.org/
     * For Windows x86 and x64  http://mingw-w64.org/
   * LLVM Clang (Mac OS X, etc.)  http://clang.llvm.org/
   * Microsoft C/C++ Compiler, part of Visual Studio  https://visualstudio.microsoft.com/
     * https://docs.microsoft.com/en-us/cpp/build/walkthrough-compiling-a-native-cpp-program-on-the-command-line
 * Gradle 5.0 or newer: https://gradle.org/releases/

To produce binary files for Android 4.0 or newer, you will also have to install:

 * Android NDK r7 or newer  http://developer.android.com/ndk/downloads/

And similarly to target iOS, you will need to install either:

 * Gluon VM  http://gluonhq.com/products/mobile/vm/  or
 * RoboVM 1.x or newer  http://robovm.mobidevelop.com/downloads/


Getting Started
---------------
To understand how [JavaCPP](https://github.com/bytedeco/javacpp) is meant to be used, one should first take a look at the [Mapping Recipes for C/C++ Libraries](https://github.com/bytedeco/javacpp/wiki/Mapping-Recipes), but a high-level overview of the [Basic Architecture](https://github.com/bytedeco/javacpp/wiki/Basic-Architecture) is also available to understand the bigger picture.

Once comfortable enough with the command line interface, the build plugin for Gradle can be used to integrate easily that workflow as part of `build.gradle` as shown below. By default, it creates a `javacppJar` task that archives the native libraries into a separate JAR file and sets its classifier to `$javacppPlatform`, while excluding those files from the default `jar` task. To customize the behavior, there are properties and extensions that can be modified and whose documentation is available as part of the source code in these files:
 * [`BuildTask.java`](src/main/java/org/bytedeco//gradle/javacpp/BuildTask.java)
 * [`BuildPlugin.java`](src/main/java/org/bytedeco//gradle/javacpp/BuildPlugin.java)

Fully functional sample projects are also provided in the [`samples`](samples) subdirectory and can be used as templates.

```groovy
plugins {
  id 'java-library'
  id 'org.bytedeco.gradle-javacpp-build' version "$javacppVersion"
}

dependencies {
    api "org.bytedeco:javacpp:$javacppVersion"
}

tasks.withType(org.bytedeco.gradle.javacpp.BuildTask) {
    // set here default values for all build tasks below, typically just includePath and linkPath
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


----
Project lead: Samuel Audet [samuel.audet `at` gmail.com](mailto:samuel.audet&nbsp;at&nbsp;gmail.com)  
Developer site: https://github.com/bytedeco/gradle-javacpp  
Discussion group: http://groups.google.com/group/javacpp-project
