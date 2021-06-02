
 * Make `BuildTask` properties `public` to allow access with Kotlin DSL ([pull #16](https://github.com/bytedeco/gradle-javacpp/issues/16))
 * Fix `BuildPlugin` incorrectly resetting `javacppBuildParser.outputDirectory` in subprojects ([pull #12](https://github.com/bytedeco/gradle-javacpp/issues/12))

### March 8, 2021 version 1.5.5
 * Add `javacppPlatformExtension` property to `BuildPlugin` and map it to `platform.extension` property
 * Add instructions to integrate `BuildTask` with Android Studio ([issue #5](https://github.com/bytedeco/gradle-javacpp/issues/5))
 * Fix `PlatformPlugin` evaluating `javacppPlatform` too early ([issue #8](https://github.com/bytedeco/gradle-javacpp/issues/8))
 * Honor the `skip` property in `BuildTask` ([pull #7](https://github.com/bytedeco/gradle-javacpp/issues/7))
 * Add `BuildExtension` helper for `maven-publish` and update zlib sample project
 * Add builds for Android to zlib sample project ([issue #5](https://github.com/bytedeco/gradle-javacpp/issues/5))

### September 9, 2020 version 1.5.4
 * Initial release
