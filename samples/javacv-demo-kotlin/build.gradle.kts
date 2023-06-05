plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    kotlin("jvm").version("1.3.72")
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    id("org.bytedeco.gradle-javacpp-platform").version("1.5.9")
}

repositories {
    jcenter()
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.bytedeco:javacv-platform:1.5.9")
}

tasks {
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE // allow duplicates
        // Otherwise you'll get a "No main manifest attribute" error
        manifest {
            attributes["Main-Class"] = "org.bytedeco.javacv.samples.Demo"
        }

        // To add all of the dependencies otherwise a "NoClassDefFoundError" error
        from(sourceSets.main.get().output)

        dependsOn(configurations.runtimeClasspath)
        from({
            configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
        })
    }
}
