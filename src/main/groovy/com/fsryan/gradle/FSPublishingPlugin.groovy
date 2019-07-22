package com.fsryan.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FSPublishingPlugin implements Plugin<Project> {

    static final Logger LOGGER = LoggerFactory.getLogger(FSPublishingPlugin.class)

    @Override
    void apply(Project project) {
        // most of this was taken from here:
        // https://stackoverflow.com/questions/34331713/publishing-android-library-aar-to-bintray-with-chosen-flavors

        def fsPublishingExt = project.extensions.create("fsPublishingConfig", FSPublishingExtension, project)
        project.afterEvaluate {
            def pomConfig = {
                developers {
                    developer {
                        id 'fsryan'
                        name 'Ryan Scott'
                        email 'fsryan.developer@gmail.com'
                    }
                }
                scm {
                    connection "${fsPublishingExt.siteUrl}.git"
                    developerConnection "${fsPublishingExt.siteUrl}.git"
                    url fsPublishingExt.siteUrl
                }
            }

            project.publishing {
                repositories {
                    maven {
                        name fsPublishingExt.releaseRepoName
                        url fsPublishingExt.releaseRepoUrl
                        credentials(AwsCredentials) {
                            accessKey = fsPublishingExt.awsAccessKeyId
                            secretKey = fsPublishingExt.awsSecretKey
                        }
                    }
                    maven {
                        name fsPublishingExt.snapshotRepoName
                        url fsPublishingExt.snapshotRepoUrl
                        credentials(AwsCredentials) {
                            accessKey = fsPublishingExt.awsAccessKeyId
                            secretKey = fsPublishingExt.awsSecretKey
                        }
                    }
                }

                publications {
                    if (isAndroidProject(project)) {
                        project.android.libraryVariants.all { variant ->
                            LOGGER.debug("creating publication for variant: ${variant.name}")
                            def variantArtifactId = "${fsPublishingExt.baseArtifactId}"
                            if (variant.flavorName != '') {
                                variantArtifactId += "-${variant.flavorName.replace('_', '-').toLowerCase()}"
                            }
                            if (variant.buildType.name == 'debug') {
                                variantArtifactId += "-${variant.buildType.name.replace('_', '-').toLowerCase()}"
                            }
                            def flavored = !variant.flavorName.isEmpty()
                            LOGGER.debug("variantArtifactId: $variantArtifactId")

                            /**
                             * If the javadoc destinationDir wasn't changed per flavor, the libraryVariants would
                             * overwrite the javaDoc as all variants would write in the same directory
                             * before the last javadoc jar would have been built, which would cause the last javadoc
                             * jar to include classes from other flavors that it doesn't include.
                             *
                             * Yes, tricky.
                             *
                             * Note that "${buildDir}/docs/javadoc" is the default javadoc destinationDir.
                             */
                            def javaDocDestDir = project.file("${project.buildDir}${File.separator}docs${File.separator}javadoc${flavored ? "${File.separator}${variant.name.replace('_', '-')}" : ""}")
                            def sourceDirs = variant.sourceSets.collect {
                                it.javaDirectories // Also includes kotlin sources if any.
                            }

                            LOGGER.debug("creating javadoc jar at destination directory: $javaDocDestDir")
                            def javadoc = project.task("${variant.name}Javadoc", type: Javadoc) {
                                description "Generates Javadoc for ${variant.name}."
                                source = variant.javaCompile.source
                                destinationDir = javaDocDestDir
                                classpath += project.files(project.android.getBootClasspath().join(File.pathSeparator))
                                classpath += project.files(project.configurations.compile)
                                options.links("http://docs.oracle.com/javase/7/docs/api/")
                                options.links("http://d.android.com/reference/")
                                exclude '**/BuildConfig.java'
                                exclude '**/R.java'
                                failOnError false
                            }

                            def javadocJar = project.task("${variant.name}JavadocJar", type: Jar, dependsOn: javadoc) {
                                description "Puts Javadoc for ${variant.name} in a jar."
                                classifier = 'javadoc'
                                from javadoc.destinationDir
                            }

                            LOGGER.debug("creating sources jar from ${sourceDirs}")
                            def sourcesJar = project.task("${variant.name}SourcesJar", type: Jar) {
                                description "Puts sources for ${variant.name} in a jar."
                                from sourceDirs
                                classifier = 'sources'
                            }

                            def publicationNames = ["${project.name}${variant.name.capitalize()}"]
                            fsPublishingExt.additionalPublications.forEach { additional ->
                                publicationNames.add("${project.name}${variant.name.capitalize()}To${additional.capitalize()}")
                            }
                            publicationNames.forEach { publicationName ->
                                "$publicationName"(MavenPublication) {
                                    artifactId = variantArtifactId
                                    groupId = fsPublishingExt.groupId
                                    version = "${fsPublishingExt.versionName}${appendingVersionSuffix(project) ? "-${project.property('fsryan.versionSuffix')}" : ''}"

                                    artifact variant.outputs[0].packageLibrary // This is the aar library
                                    artifact sourcesJar
                                    artifact javadocJar

                                    pom {
                                        packaging 'aar'
                                        withXml {
                                            def root = asNode()
                                            if (fsPublishingExt.description != "") {
                                                root.appendNode('description', fsPublishingExt.description)
                                            }
                                            root.appendNode("name", variantArtifactId)
                                            root.appendNode("url", fsPublishingExt.siteUrl)
                                            root.children().last() + pomConfig

                                            // add properties
                                            def propsNode = root["properties"][0] ?: root.appendNode("properties")
                                            fsPublishingExt.extraPomProperties.forEach { k,v ->
                                                propsNode.appendNode(k, v)
                                            }

                                            // add dependencies
                                            def depsNode = root["dependencies"][0] ?: root.appendNode("dependencies")
                                            def addDep = {
                                                if (it.group == null) {
                                                    LOGGER.debug("$publicationName: Not adding dependency $it: group was null")
                                                    return  // Avoid empty dependency nodes
                                                }

                                                LOGGER.debug("$publicationName: Adding dependency ${it.group}:${it.name}:${it.version}")
                                                def dependencyNode = depsNode.appendNode('dependency')
                                                dependencyNode.appendNode('groupId', it.group)
                                                dependencyNode.appendNode('artifactId', it.name)
                                                dependencyNode.appendNode('version', it.version)
                                                if (it.hasProperty('optional') && it.optional) {
                                                    dependencyNode.appendNode('optional', 'true')
                                                }
                                            }

                                            // Add deps that each variant has
                                            project.configurations.implementation.allDependencies.each addDep
                                            // add deps specified for variants of this build type
                                            project.configurations["${variant.buildType.name}Implementation"].allDependencies.each addDep
                                            if (flavored) {
                                                project.configurations["${variant.flavorName}Implementation"].allDependencies.each addDep
                                                project.configurations["${variant.flavorName}${variant.buildType.name.capitalize()}Implementation"].allDependencies.each addDep
                                                project.configurations["${variant.name}Implementation"].allDependencies.each addDep
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else /* assume java library */ {

                        def sourcesJar = project.tasks.findByName("sourcesJar") ?: project.task("sourcesJar", type: Jar,  dependsOn: 'classes') {
                            from project.sourceSets.main.allJava
                            archiveClassifier = 'sources'
                        }

                        def javadocJar = project.tasks.findByName("javadocJar") ?: project.task("javadocJar", type: Jar, dependsOn: 'javadoc') {
                            from project.javadoc
                            archiveClassifier = 'javadoc'
                        }

                        def publicationNames = ["maven"]
                        fsPublishingExt.additionalPublications.forEach { additional ->
                            publicationNames.add("mavenTo${additional.capitalize()}")
                        }
                        publicationNames.forEach { publicationName ->
                            "$publicationName"(MavenPublication) {
                                from project.components.java
                                artifact sourcesJar
                                artifact javadocJar
                                groupId fsPublishingExt.groupId
                                artifactId fsPublishingExt.baseArtifactId
                                version "${fsPublishingExt.versionName}${appendingVersionSuffix(project) ? "-${project.property('fsryan.versionSuffix')}" : ''}"
                                pom.withXml {
                                    def root = asNode()
                                    if (fsPublishingExt.description != "") {
                                        root.appendNode('description', fsPublishingExt.description)
                                    }
                                    root.appendNode('name', fsPublishingExt.baseArtifactId)
                                    root.appendNode('url', fsPublishingExt.siteUrl)
                                    root.children().last() + pomConfig

                                    // add properties
                                    def propsNode = root["properties"][0] ?: root.appendNode("properties")
                                    fsPublishingExt.extraPomProperties.forEach { k,v ->
                                        propsNode.appendNode(k, v)
                                    }

                                    def depsNode = root["dependencies"][0] ?: root.appendNode("dependencies")
                                    // Add deps that everyone has
                                    project.configurations.implementation.allDependencies.each {
                                        if (it.group == null) {
                                            LOGGER.debug("maven: Not adding dependency $it: group was null")
                                            return  // Avoid empty dependency nodes
                                        }

                                        LOGGER.debug("maven: Adding dependency ${it.group}:${it.name}:${it.version}")
                                        def dependencyNode = depsNode.appendNode('dependency')
                                        dependencyNode.appendNode('groupId', it.group)
                                        dependencyNode.appendNode('artifactId', it.name)
                                        dependencyNode.appendNode('version', it.version)
                                        if (it.hasProperty('optional') && it.optional) {
                                            dependencyNode.appendNode('optional', 'true')
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isAndroidProject(project)) {
                project.android.libraryVariants.all { variant ->
                    def publicationName = "${project.name.capitalize()}${variant.name.capitalize()}"
                    if (variant.buildType.name == 'debug') {
                        // We never want to publish a debug variant to the
                        // release repository. This will help us to have a
                        // clean release repository that is free of any
                        // developer-only builds.
                        disablePublishTask(project, publicationName, fsPublishingExt.releaseRepoName)
                        createReleaseAlias(project, "release${variant.name.capitalize()}", publicationName, fsPublishingExt.snapshotRepoName)
                    } else {
                        createReleaseAlias(project, "release${variant.name.capitalize().replace("Release", "")}", publicationName, fsPublishingExt.releaseRepoName)
                        createReleaseAlias(project, "release${variant.name.capitalize().replace("Release", "")}${fsPublishingExt.snapshotRepoName.capitalize()}", publicationName, fsPublishingExt.snapshotRepoName)
                    }
                }
            } else /* assume java project */ {
                project.javadoc.failOnError false
                def javadocJarTask = project.tasks.findByName("javadocJar")
                def sourcesJarTask = project.tasks.findByName("sourcesJar")

                project.artifacts {
                    archives javadocJarTask
                    archives sourcesJarTask
                }

                createReleaseAlias(project, "release", "Maven", fsPublishingExt.releaseRepoName)
                createReleaseAlias(project, "release${fsPublishingExt.snapshotRepoName.capitalize()}", "Maven", fsPublishingExt.snapshotRepoName)
            }
        }
    }

    static void createReleaseAlias(Project p, String taskName, String publicationName, String destinationRepo) {
        def releaseTask = p.tasks.create(
                name: taskName,
                dependsOn: [publicationTaskName(publicationName, destinationRepo)]
        )
        releaseTask.group = 'publishing'
        releaseTask.description = "publish ${p.name}'s $publicationName publication to $destinationRepo repository"
        releaseTask.doLast {
            println "Huzzah! Successfully published ${p.name}'s $publicationName publication to $destinationRepo repository"
        }
    }

    static void disablePublishTask(Project p, String publicationName, String destinationRepo) {
        def taskName = publicationTaskName(publicationName, destinationRepo)
        def publishTask = p.tasks.findByName(taskName)
        if (publishTask == null) {
            p.tasks.whenObjectAdded { t ->
                if (t.name == taskName) {
                    LOGGER.debug("Disabling task: ${t.path}")
                    t.setEnabled(false)
                }
            }
        } else {
            publishTask.setEnabled(false)
        }
    }

    static String publicationTaskName(String publicationName, String destinationRepo) {
        return "publish${publicationName}PublicationTo${destinationRepo.capitalize()}Repository"
    }

    static boolean isAndroidProject(Project p) {
        return p.plugins.hasPlugin('com.android.application') || p.plugins.hasPlugin('com.android.library')
    }

    static boolean appendingVersionSuffix(Project p) {
        return p.hasProperty('fsryan.versionSuffix')
    }
}
