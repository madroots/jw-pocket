plugins {
    java
}

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

/*
 * A small desktop launcher that runs the real phone app in the Android
 * emulator: `java -jar build/libs/jw-pocket-preview.jar` opens it in an
 * emulator window, `--screenshot shot.png` saves a picture of it and exits.
 */
tasks.jar {
    archiveFileName.set("jw-pocket-preview.jar")
    manifest { attributes("Main-Class" to "app.jwpocket.preview.Preview") }
}

// Put the launcher where tools look for a program: build/libs at the project root.
val copyPreview by tasks.registering(Copy::class) {
    from(tasks.jar)
    into(rootProject.layout.buildDirectory.dir("libs"))
}
tasks.assemble { finalizedBy(copyPreview) }
