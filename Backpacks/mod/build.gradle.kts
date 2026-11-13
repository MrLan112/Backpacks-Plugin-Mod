plugins {
    id("fabric-loom") version "1.20.0-alpha.27"
}

base {
    archivesName.set("backpacks-mod")
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    // 26.x is shipped deobfuscated, Loom detects that and needs no mapping set.
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.166.2+26.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
