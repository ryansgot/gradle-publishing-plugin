# fsryan-gradle-publishing
A gradle plugin for publishing libraries

Currently, you can use this plugin to publish either jar or aar artifacts. This means you can write your project in Java/Kotlin/Groovy/etc. and deploy a binary artifact to an S3 repo.

## How to use
1. In your project's root build.gradle file, add the following:
```groovy
buildscript {
  repositories {
    jcenter()
    maven {
      url 's3://your S3 bucket'
      authentication {
        credentials(AwsCredentials) {
          accessKey = /* your access key here, but you shouldn't store in source control */
          secretKey = /* your secret key here, but you shouldn't store in source control */
        }
      }
    }
  }
  dependencies {
    classpath 'com.fsryan.gradle:fsryan-gradle-publishing:x.y.z'
    classpath 'com.github.dcendents:android-maven-gradle-plugin:2.1'  // <-- for Android only
  }
}
```
2. Then apply these plugins in order in the build.gradle file of the project you want to publish
```groovy
apply plugin: 'com.android.library'                 // <-- for android only use 'java' or 'java-library' for java/kotlin/groovy
apply plugin: 'maven-publish'
apply plugin: 'com.github.dcendents.android-maven'  // <-- for android only
apply plugin: 'fsryan-gradle-publishing'
```
3. Then add the `fsPublishingConfig` gradle extension to configure the plugin:
```groovy
fsPublishingConfig {
  developerName "Your name"
  developerId "Your identifier"
  developerEmail "Your email address"
  siteUrl /* url to your project's site (github, etc.) */
  baseArtifactId /* the name of your library */
  groupId project.group                                     // <-- the group name (such as com.fsryan)
  versionName project.version                               // <-- the version name (such as semantic version 1.0.3)
  extraPomProperties = [                                    // <-- all of the extra pom properties to add
    'myproj.gitHash': getGitHash()                          // <-- assuming you can get the git hash
  ]
  // goes in the description field of the pom
  description = /* A short description of the artifact you're publishing */
}
```
There are other properties you can configure via the `fsPublishingConfig` gradle extension. See [FSPublishingExtension](src/main/groovy/com/fsryan/gradle/FSPublishingExtension.groovy) for more details.


## Developer setup:
If you want to develop without an IDE, then this repository has all you need.
If you want to develop in IntelliJ, then you need to . . .
1. Download the Groovy SDK: http://groovy-lang.org/download.html
2. Set your Groovy SDK in your IDE properties