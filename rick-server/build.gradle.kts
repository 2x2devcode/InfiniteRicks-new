plugins {
    application
}

application {
    mainClass.set("com.infinitericks.wallet.server.RickServer")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":rick-core"))
    implementation(project(":rick-api"))
    implementation("io.javalin:javalin:6.3.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runApi") {
    group = "application"
    description = "Run official JSON API on 127.0.0.1:40002"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.infinitericks.wallet.server.RickServer")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runExplorer") {
    group = "application"
    description = "Run explorer JSON API on 127.0.0.1:40051"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.infinitericks.wallet.server.RickExplorerServer")
    standardInput = System.`in`
}
