plugins {
    java
}

group = property("group") as String
version = property("version") as String
val jvmVersion = providers.gradleProperty("jvmVersion").get().toInt()
val paperApiVersion = providers.gradleProperty("paperApiVersion").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(jvmVersion)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(mapOf("version" to project.version))
    }
}
