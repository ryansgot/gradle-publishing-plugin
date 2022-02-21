package com.fsryan.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.artifacts.repositories.PasswordCredentials
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
                        id fsPublishingExt.developerId
                        name fsPublishingExt.developerName
                        email fsPublishingExt.developerEmail
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
                    if (fsPublishingExt.useBasicCredentials) {
                        if (fsPublishingExt.releaseRepositoryConfigured()) {
                            maven {
                                name fsPublishingExt.releaseRepoName
                                url fsPublishingExt.releaseRepoUrl
                                credentials(PasswordCredentials) {
                                    username = fsPublishingExt.releaseBasicUser
                                    password = fsPublishingExt.releaseBasicPassword
                                }
                            }
                        }
                        if (fsPublishingExt.snapshotRepositoryConfigured()) {
                            maven {
                                name fsPublishingExt.snapshotRepoName
                                url fsPublishingExt.snapshotRepoUrl
                                credentials(PasswordCredentials) {
                                    username = fsPublishingExt.snapshotBasicUser
                                    password = fsPublishingExt.snapshotBasicPassword
                                }
                            }
                        }
                    } else {
                        if (fsPublishingExt.releaseRepositoryConfigured()) {
                            maven {
                                name fsPublishingExt.releaseRepoName
                                url fsPublishingExt.releaseRepoUrl
                                credentials(AwsCredentials) {
                                    accessKey = fsPublishingExt.awsAccessKeyId
                                    secretKey = fsPublishingExt.awsSecretKey
                                }
                            }
                        }
                        if (fsPublishingExt.snapshotRepositoryConfigured()) {
                            maven {
                                name fsPublishingExt.snapshotRepoName
                                url fsPublishingExt.snapshotRepoUrl
                                credentials(AwsCredentials) {
                                    accessKey = fsPublishingExt.awsAccessKeyId
                                    secretKey = fsPublishingExt.awsSecretKey
                                }
                            }
                        }
                    }
                }

                publications {
                    if (isAndroidProject(project)) {
                        project.android.libraryVariants.all { variant ->
                            if (fsPublishingExt.filteredAndroidVariants.contains(variant.name)) {
                                LOGGER.debug("Filtered variant: ${variant.name}")
                            } else {
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
                                    options.links("http://docs.oracle.com/javase/7/docs/api/")
                                    options.links("http://d.android.com/reference/")
                                    exclude '**/BuildConfig.java'
                                    exclude '**/R.java'
                                    failOnError false
                                }

                                def javadocJar = project.tasks.findByName("${variant.name}JavadocJar")
                                if (javadocJar == null) {
                                    javadocJar = project.task("${variant.name}JavadocJar", type: Jar)
                                }
                                javadocJar.dependsOn(javadoc)
                                javadocJar.description = "Puts Javadoc for ${variant.name} in a jar."
                                javadocJar.classifier = 'javadoc'
                                javadocJar.from javadoc.destinationDir

                                LOGGER.debug("creating sources jar from ${sourceDirs}")
                                def sourcesJar = project.tasks.findByName("${variant.name}SourcesJar")
                                if (sourcesJar == null) {
                                    sourcesJar = project.task("${variant.name}SourcesJar", type: Jar)
                                }
                                sourcesJar.description = "Puts sources for ${variant.name} in a jar."
                                sourcesJar.from sourceDirs
                                sourcesJar.classifier = 'sources'

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

                                            licenses {
                                                license {
                                                    name = fsPublishingExt.licenseName
                                                    url = fsPublishingExt.licenseUrl
                                                    distribution = fsPublishingExt.licenseDistribution
                                                    comments = fsPublishingExt.licenseComments
                                                }
                                            }

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

                                                Set<String> dependenciesAdded = new HashSet<>()

                                                // add dependencies
                                                def depsNode = root["dependencies"][0] ?: root.appendNode("dependencies")
                                                def addDep = { dep ->
                                                    if (dep.group == null) {
                                                        LOGGER.debug("$publicationName: Not adding dependency $dep: group was null")
                                                        return  // Avoid empty dependency nodes
                                                    }
                                                    if (dep.name == null) {
                                                        LOGGER.debug("$publicationName: Not adding dependency $dep: name was null")
                                                        return  // Avoid empty dependency nodes
                                                    }
                                                    if (dep.version == null) {
                                                        LOGGER.debug("$publicationName: Not adding dependency $dep: version was null")
                                                        return  // Avoid empty dependency nodes
                                                    }

                                                    String name = dep.name
                                                    Map<String, String> publicationOverrides = fsPublishingExt.dependencyNameOverrides[publicationName]
                                                    if (publicationOverrides != null) {
                                                        String nameOverride = publicationOverrides[name]
                                                        if (nameOverride != null) {
                                                            name = nameOverride
                                                            LOGGER.debug("$publicationName: Overriding dependency $dep with name '$nameOverride'")
                                                        }
                                                    }

                                                    if (dependenciesAdded.contains("${dep.group}:$name:${dep.version}")) {
                                                        LOGGER.debug("$publicationName: not adding $dep: ALREADY ADDED")
                                                        return
                                                    }

                                                    LOGGER.debug("$publicationName: Adding dependency ${dep.group}:$name:${dep.version}")
                                                    def dependencyNode = depsNode.appendNode('dependency')
                                                    dependencyNode.appendNode('groupId', dep.group)
                                                    dependencyNode.appendNode('artifactId', name)
                                                    dependencyNode.appendNode('version', dep.version)
                                                    if (dep.hasProperty('optional') && dep.optional) {
                                                        dependencyNode.appendNode('optional', 'true')
                                                    }

                                                    dependenciesAdded.add("${dep.group}:$name:${dep.version}")
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
                        }
                    } else /* assume java library */ {

                        def sourcesJar = project.tasks.findByName("sourcesJar") ?: project.task("sourcesJar", type: Jar,  dependsOn: 'classes') {
                            from project.sourceSets.main.allSource
                            archiveClassifier = 'sources'
                        }

                        def javadocJar = project.tasks.findByName("javadocJar") ?: project.task("javadocJar", type: Jar, dependsOn: 'javadoc') {
                            from (isUsingDokka(project) ? project.dokkaJavadoc.outputDirectory : project.javadoc.destinationDir)
                            archiveClassifier = 'javadoc'
                        }

                        if (isUsingDokka(project)) {
                            javadocJar.dependsOn(project.dokkaJavadoc)
                        }

                        project.javadoc.failOnError = false
                        project.artifacts.add("archives", sourcesJar)
                        project.artifacts.add("archives", javadocJar)

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

                                pom.licenses {
                                    license {
                                        name = fsPublishingExt.licenseName
                                        url = fsPublishingExt.licenseUrl
                                        distribution = fsPublishingExt.licenseDistribution
                                        comments = fsPublishingExt.licenseComments
                                    }
                                }

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

                                    Set<String> dependenciesAdded = new HashSet<>()

                                    def depsNode = root["dependencies"][0] ?: root.appendNode("dependencies")
                                    // Add deps that everyone has
                                    project.configurations.implementation.allDependencies.each { dep ->
                                        if (dep.group == null) {
                                            LOGGER.debug("maven: Not adding dependency $dep: group was null")
                                            return  // Avoid empty dependency nodes
                                        }
                                        if (dep.group == null) {
                                            LOGGER.debug("$publicationName: Not adding dependency $dep: group was null")
                                            return  // Avoid empty dependency nodes
                                        }
                                        if (dep.name == null) {
                                            LOGGER.debug("$publicationName: Not adding dependency $dep: name was null")
                                            return  // Avoid empty dependency nodes
                                        }
                                        if (dep.version == null) {
                                            LOGGER.debug("$publicationName: Not adding dependency $dep: version was null")
                                            return  // Avoid empty dependency nodes
                                        }

                                        String name = dep.name
                                        Map<String, String> publicationOverrides = fsPublishingExt.dependencyNameOverrides[publicationName]
                                        if (publicationOverrides != null) {
                                            String nameOverride = publicationOverrides[name]
                                            if (nameOverride != null) {
                                                name = nameOverride
                                                LOGGER.debug("$publicationName: Overriding dependency $dep with name '$nameOverride'")
                                            }
                                        }

                                        if (dependenciesAdded.contains("${dep.group}:$name:${dep.version}")) {
                                            LOGGER.debug("$publicationName: not adding $dep: ALREADY ADDED")
                                            return
                                        }

                                        LOGGER.debug("maven: Adding dependency ${dep.group}:${name}:${dep.version}")
                                        def dependencyNode = depsNode.appendNode('dependency')
                                        dependencyNode.appendNode('groupId', dep.group)
                                        dependencyNode.appendNode('artifactId', name)
                                        dependencyNode.appendNode('version', dep.version)
                                        if (dep.hasProperty('optional') && dep.optional) {
                                            dependencyNode.appendNode('optional', 'true')
                                        }

                                        dependenciesAdded.add("${dep.group}:$name:${dep.version}")
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
                        createReleaseAlias(project, "release${variant.name.capitalize()}", publicationName, fsPublishingExt.releaseRepoName)
                        createReleaseAlias(project, "release${variant.name.capitalize()}${fsPublishingExt.snapshotRepoName.capitalize()}", publicationName, fsPublishingExt.snapshotRepoName)
                    } else {
                        createReleaseAlias(project, "release${variant.name.capitalize().replace("Release", "")}", publicationName, fsPublishingExt.releaseRepoName)
                        createReleaseAlias(project, "release${variant.name.capitalize().replace("Release", "")}${fsPublishingExt.snapshotRepoName.capitalize()}", publicationName, fsPublishingExt.snapshotRepoName)
                    }
                }
            } else /* assume java project */ {
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

    static boolean isUsingDokka(Project p) {
        return (isAndroidProject(p) && p.plugins.hasPlugin("org.jetbrains.dokka-android")) ||  p.plugins.hasPlugin("org.jetbrains.dokka")
    }
}
