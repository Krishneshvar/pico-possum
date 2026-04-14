plugins {
    java
    application
    jacoco
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "9.3.2"
    id("org.flywaydb.flyway") version "12.3.0"
}

flyway {
    url = "jdbc:sqlite:possum.db"
    locations = arrayOf("filesystem:src/main/resources/sql/migrations")
    mixed = true
}

group = "com.possum"
version = "1.0.0"

repositories {
    mavenCentral()
}

val junitVersion = "5.12.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--enable-native-access=javafx.graphics")
}

application {
    mainClass.set("com.possum.AppLauncher")
}

javafx {
    version = "21.0.6"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.flywaydb:flyway-core:12.3.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("org.mindrot:jbcrypt:0.4")
    
    // Connection Pooling
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // Export Support
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    
    // Icons
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-boxicons-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-fontawesome5-pack:12.3.1")

    testImplementation(platform("org.junit:junit-bom:${junitVersion}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    maxParallelForks = Runtime.getRuntime().availableProcessors().div(2).coerceAtLeast(1)
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it).matching {
            exclude("com/possum/ui/**", "com/possum/AppLauncher*")
        }
    }))
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.25".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.shadowJar {
    archiveBaseName.set("possum")
    archiveVersion.set("")
    archiveClassifier.set("all")
    manifest {
        attributes("Main-Class" to "com.possum.AppLauncher")
    }
    mergeServiceFiles()
}

tasks.register<Exec>("createInstaller") {
    dependsOn("shadowJar")
    
    doFirst {
        val installerDir = file("build/installer")
        if (installerDir.exists()) {
            installerDir.deleteRecursively()
        }
        installerDir.mkdirs()
    }
    
    commandLine(
        "jpackage",
        "--type", "msi",
        "--input", "build/libs",
        "--main-jar", "possum-all.jar",
        "--main-class", "com.possum.AppLauncher",
        "--name", "POSSUM",
        "--vendor", "POSSUM",
        "--app-version", "1.0",
        "--description", "Point Of Sale Solution for Unified Management",
        "--dest", "build/installer",
        "--java-options", "-Xms128m",
        "--java-options", "-Xmx512m",
        "--java-options", "-XX:+UseZGC",
        "--java-options", "-XX:+ZGenerational",
        "--icon", "src/main/resources/icons/icon." + (if (System.getProperty("os.name").lowercase().contains("win")) "ico" else if (System.getProperty("os.name").lowercase().contains("mac")) "icns" else "png"),
        "--win-shortcut",
        "--win-menu"
    )
}
