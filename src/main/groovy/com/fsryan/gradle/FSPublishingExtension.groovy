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
    String awsAccessKeyId
    String awsSecretKey
    /**
     * <p>If you want to add properties to the pom, then you can do so via this
     * map. For example, if you want to leave some breadcrumbs that take you
     * back to a particular revision of your repository, you can do so here.
     * The properties block is not really intended for this, but it allows you
     * to write your own custom tags, so you can take advantage of it for such.
     */
    Map<String, String> extraPomProperties = Collections.emptyMap()

    FSPublishingExtension(Project project) {
        groupId = project.group
        versionName = project.name
    }
}