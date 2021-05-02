package com.fsryan.gradle

import org.gradle.api.Project

class FSPublishingExtension {
    String developerName = ""
    String developerId = ""
    String developerEmail = ""
    String siteUrl
    String baseArtifactId
    String groupId
    String versionName
    String releaseRepoName = 'release'
    String releaseRepoUrl
    String snapshotRepoName = 'snapshot'
    String snapshotRepoUrl
    String description = ""
    String licenseName = ""
    String licenseUrl = ""
    String licenseDistribution = ""
    String licenseComments = ""
    /**
     * Your AWS access key id--which you configure via the AWS console
     */
    String awsAccessKeyId
    /**
     * Your AWS secret key--which you configure via the AWS console.
     */
    String awsSecretKey
    /**
     * If you want to use basic creds, then you should use this. Additionally,
     * you should flip {@link #useBasicCredentials} to false.
     */
    String releaseBasicUser
    /**
     * If you want to use basic creds, then you should use this. Additionally,
     * you should flip {@link #useBasicCredentials} to false.
     */
    String releaseBasicPassword
    /**
     * If you want to use basic creds, then you should use this. Additionally,
     * you should flip {@link #useBasicCredentials} to false.
     */
    String snapshotBasicUser
    /**
     * If you want to use basic creds, then you should use this. Additionally,
     * you should flip {@link #useBasicCredentials} to false.
     */
    String snapshotBasicPassword
    /**
     * Set this if you want to use basic credentials instead of the
     * previously-supported AWS credentials. Defaults to false for
     * compatibility.
     */
    boolean useBasicCredentials = false
    List<String> additionalPublications = new ArrayList<>()
    /**
     * <p>If you want to add properties to the pom, then you can do so via this
     * map. For example, if you want to leave some breadcrumbs that take you
     * back to a particular revision of your repository, you can do so here.
     * The properties block is not really intended for this, but it allows you
     * to write your own custom tags, so you can take advantage of it for such.
     */
    Map<String, String> extraPomProperties = Collections.emptyMap()

    /**
     * <p>Some dependencies that should be redirected to different artifacts
     * than are listed in the configuration. A basic example is the case in
     * which multiple libraries are published from the same project. If one
     * of those projects depends upon the other, then you may want to depend
     * upon one configuration over another.
     * <p>A concrete example is the `release`/`debug` buildType that allows you
     * to define different classes/resources for different configurations in an
     * Android build. If you are publishing a library that depends upon a
     * sibling library's `debug` configuration, then you can override the
     * artifactId in the generated pom file by adding an entry in this map.
     * <p>The overrides are publication-specific, so the key in the outer map
     * is the name of the publication (use `./gradlew :lib:tasks` to find the
     * exact names of the publications). The inner map key is from the
     * artifactId that will be picked up as a dependency to the overridden
     * artifact name.
     */
    Map<String, Map<String, String>> dependencyNameOverrides = Collections.emptyMap()

    FSPublishingExtension(Project project) {
        groupId = project.group
        versionName = project.name
    }

    boolean hasReleaseBasicCreds() {
        return releaseBasicUser != null && releaseBasicPassword != null
    }

    boolean hasAwsCreds() {
        return awsAccessKeyId != null && awsSecretKey != null
    }

    boolean hasSnapshotBasicCreds() {
        return snapshotBasicUser != null && snapshotBasicPassword != null
    }

    boolean releaseRepositoryConfigured() {
        return releaseRepoUrl != null && (hasReleaseBasicCreds() || hasAwsCreds())
    }

    boolean snapshotRepositoryConfigured() {
        return snapshotRepoUrl != null && (hasSnapshotBasicCreds() || hasAwsCreds())
    }
}