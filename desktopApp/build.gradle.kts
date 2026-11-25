import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.internal.os.OperatingSystem
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

// the java the app ships with: jlink trims it, jpackage bundles it, and it reads the AOT cache
// the build trains (below). gradle finds it among the jdks installed on the machine. every build
// configures this project, and only packaging needs it, so only packaging fails without it
val runtimeJava = 27
val runtimeJdk = runCatching {
    javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(runtimeJava) }.get()
        .metadata.installationPath.asFile.absolutePath
}.getOrNull()

compose.desktop {
    application {
        mainClass = "com.joshlong.mogul.client.desktop.MainKt"
        runtimeJdk?.let { javaHome = it }
        // skiko loads its native library itself, which java will one day refuse without this
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // a heap of its own, not java's default of a quarter of the machine's memory: past 32g,
        // on a machine with 128g or more, java stops compressing pointers, and an AOT cache
        // trained with them compressed (or without) won't load. a ceiling, not a reservation:
        // big enough for an upload, which is read into memory whole
        jvmArgs += "-Xmx8g"
        // a release knows its version, and asks github for the latest release as it opens
        // (UpdateNotice). run from gradle without a version, it doesn't ask
        providers.gradleProperty("mogul.version").orNull?.let { version ->
            jvmArgs += listOf(
                "-Dmogul.version=$version",
                "-Dmogul.releases=https://api.github.com/repos/bootiful-media-mogul/studio-compose/releases/latest",
            )
        }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Mogul Studio"
            // bin/desktop.sh sets it for a release. a dmg wants its first number above 0, and an
            // msi wants each of the three no higher than 255.255.65535
            packageVersion = providers.gradleProperty("mogul.version").orNull ?: "1.0.0"
            // the logo, as bin/brand.sh makes it for each system
            macOS {
                bundleID = "com.joshlong.mogul.studio"
                iconFile.set(project.file("icons/mogul.icns"))
            }
            windows {
                iconFile.set(project.file("icons/mogul.ico"))
            }
            linux {
                iconFile.set(project.file("icons/mogul.png"))
            }
            // ktor's okhttp engine and javafx's media player reach for these at runtime
            modules("java.net.http", "jdk.unsupported", "java.naming")
        }
    }
}

/**
 * trains the packaged app's AOT cache (project leyden: JEPs 483, 514 and 515) and puts it in the
 * app, so the app starts with its classes already loaded and linked and its hot methods already
 * profiled. it runs the app as it was packaged, through its own launcher, since the java inside
 * has no `java` command: once to record what it loads and runs while it draws its first screen
 * (until Main.kt's `mogul.aot.training` seconds are up), and once to make the cache of that.
 *
 * the cache fits only the java and the jars it was trained with, down to the jars' timestamps,
 * and java checks: an app whose jars don't match starts as it would without it. jpackage copies
 * the app, with new timestamps, into each installer it makes, so the cache is trained on the
 * copy inside the installer, opened up for it. installing from a .dmg or a .deb keeps the
 * timestamps. an .msi can't be opened up and made again, so windows goes without
 *
 * a mac app is sealed by its signature, so the cache, added after, is sealed in by signing the
 * app again, as whoever signed it first (ad hoc, unless it was signed for a release)
 */
abstract class AotCacheTask : DefaultTask() {

    @get:Inject
    abstract val exec: ExecOperations

    @get:Internal
    abstract val packageName: Property<String>

    /** how long the training run runs: long enough to draw the first screen and settle */
    @get:Internal
    abstract val seconds: Property<Int>

    /** the keychain the signing certificate is in, if not the default one */
    @get:Internal
    abstract val keychain: Property<String>

    @get:Internal
    protected val mac = System.getProperty("os.name").startsWith("Mac")

    /** trains the cache of the app somewhere under [root], and puts it in the app */
    protected fun train(root: File) {
        val name = this.packageName.get()
        // the launcher's configuration sits with the jars, wherever each system keeps them. a
        // .dmg links to /Applications, which mustn't be searched
        val cfg = root.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.single { it.name == "$name.cfg" }
        val app = cfg.parentFile
        val launcher = when {
            this.mac -> app.resolve("../MacOS/$name")
            System.getProperty("os.name").startsWith("Windows") -> app.resolve("../$name.exe")
            else -> app.resolve("../../bin/$name")
        }.normalize()

        val cache = app.resolve("app.aot")
        val option = "java-options=-XX:AOTCache=\$APPDIR/${cache.name}"
        cache.delete()
        cfg.writeText(cfg.readLines().filterNot { it == option }.joinToString("\n", postfix = "\n"))

        val work = this.temporaryDir.resolve("training").apply { deleteRecursively(); mkdirs() }
        val configuration = work.resolve("app.aotconf")
        val trained = work.resolve("app.aot")
        fun run(vararg options: String) {
            this.exec.exec {
                commandLine(launcher.absolutePath)
                environment("JAVA_TOOL_OPTIONS", options.joinToString(" ") { "\"$it\"" })
            }
        }
        this.logger.lifecycle("training the AOT cache: running $launcher for ${this.seconds.get()}s")
        run("-XX:AOTMode=record", "-XX:AOTConfiguration=$configuration", "-Dmogul.aot.training=${this.seconds.get()}")
        check(configuration.isFile) { "the training run recorded nothing in $configuration" }
        run("-XX:AOTMode=create", "-XX:AOTConfiguration=$configuration", "-XX:AOTCache=$trained")
        check(trained.isFile) { "no AOT cache was made from $configuration" }
        // copied, not moved: the timestamps of the jars are what matter, not the cache's
        trained.copyTo(cache)
        cfg.appendText("$option\n")
        work.deleteRecursively()
        this.logger.lifecycle("the AOT cache, ${cache.length() / (1024 * 1024)} MB, is in $cache")

        if (this.mac) this.resign(app.resolve("../..").normalize())
    }

    private fun resign(bundle: File) {
        val details = ByteArrayOutputStream()
        this.exec.exec {
            commandLine("codesign", "--display", "--verbose=2", bundle.absolutePath)
            errorOutput = details
        }
        val authority = details.toString().lines().firstOrNull { it.startsWith("Authority=") }?.removePrefix("Authority=")
        val keychain = this.keychain.orNull?.takeIf { it.isNotBlank() }
        this.exec.exec {
            commandLine(
                buildList {
                    addAll(listOf("codesign", "--force", "--preserve-metadata=identifier,entitlements,requirements,flags,runtime"))
                    if (authority == null) addAll(listOf("--sign", "-"))
                    else addAll(listOf("--timestamp", "--sign", authority))
                    if (authority != null && keychain != null) addAll(listOf("--keychain", keychain))
                    add(bundle.absolutePath)
                },
            )
        }
        this.exec.exec { commandLine("codesign", "--verify", "--deep", "--strict", bundle.absolutePath) }
        this.logger.lifecycle("signed $bundle again${authority?.let { " as $it" } ?: ", ad hoc"}, with its AOT cache")
    }

    protected fun run(vararg command: String) {
        this.exec.exec { commandLine(*command) }
    }
}

/** the app as createDistributable leaves it, for runDistributable: nothing copies it after */
abstract class TrainAppAotCache : AotCacheTask() {

    @get:Internal
    abstract val distribution: DirectoryProperty

    @TaskAction
    fun trainApp() = this.train(this.distribution.get().asFile)
}

/** the app inside the .dmg, which is made writable, and bigger, for the cache, then compressed again */
abstract class TrainDmgAotCache : AotCacheTask() {

    @get:Internal
    abstract val installers: DirectoryProperty

    @TaskAction
    fun trainDmg() {
        val dmg = this.installers.get().asFile.listFiles { it: File -> it.extension == "dmg" }!!.single()
        val work = this.temporaryDir.apply { deleteRecursively(); mkdirs() }
        val writable = work.resolve("writable.dmg")
        val volume = work.resolve("volume").apply { mkdirs() }
        val compressed = work.resolve("compressed.dmg")
        this.run("hdiutil", "convert", "-quiet", dmg.absolutePath, "-format", "UDRW", "-o", writable.absolutePath)
        this.run("hdiutil", "resize", "-quiet", "-size", "${writable.length() / (1024 * 1024) + 512}m", writable.absolutePath)
        this.run("hdiutil", "attach", "-quiet", writable.absolutePath, "-nobrowse", "-noautoopen", "-noverify", "-mountpoint", volume.absolutePath)
        try {
            this.train(volume)
            // what macos keeps on a volume mounted writable, which the .dmg didn't have
            volume.resolve(".fseventsd").deleteRecursively()
        } finally {
            this.run("hdiutil", "detach", "-quiet", volume.absolutePath, "-force")
        }
        this.run("hdiutil", "convert", "-quiet", writable.absolutePath, "-format", "UDZO", "-imagekey", "zlib-level=9", "-o", compressed.absolutePath)
        compressed.copyTo(dmg, overwrite = true)
        work.deleteRecursively()
    }
}

/** the app inside the .deb, unpacked for the cache and packed again */
abstract class TrainDebAotCache : AotCacheTask() {

    @get:Internal
    abstract val installers: DirectoryProperty

    @TaskAction
    fun trainDeb() {
        val deb = this.installers.get().asFile.listFiles { it: File -> it.extension == "deb" }!!.single()
        val work = this.temporaryDir.apply { deleteRecursively(); mkdirs() }
        val root = work.resolve("root")
        val packed = work.resolve("packed.deb")
        this.run("dpkg-deb", "--raw-extract", deb.absolutePath, root.absolutePath)
        this.train(root)
        // the cache is a file of the package's now: dpkg checks the sums it has on record
        // the cache is a file of the package's now, and the configuration has changed: dpkg
        // checks the sums it has on record
        root.resolve("DEBIAN/md5sums").takeIf { it.isFile }?.let { sums ->
            val cache = root.walkTopDown().single { it.name == "app.aot" }
            val changed = listOf(cache, cache.resolveSibling("${this.packageName.get()}.cfg"))
                .associate { it.relativeTo(root).invariantSeparatorsPath to it }
            val kept = sums.readLines().filter { line -> line.isNotBlank() && changed.keys.none { line.endsWith("  $it") } }
            val updated = changed.map { (path, file) ->
                MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString("") { "%02x".format(it) } + "  " + path
            }
            sums.writeText((kept + updated).joinToString("\n", postfix = "\n"))
        }
        this.run("dpkg-deb", "--root-owner-group", "--build", root.absolutePath, packed.absolutePath)
        packed.copyTo(deb, overwrite = true)
        work.deleteRecursively()
    }
}

if (runtimeJdk == null) {
    val missing = "the desktop app is packaged with java $runtimeJava, and gradle found none installed"
    tasks.matching { it.name == "checkRuntime" }.configureEach { doFirst { throw GradleException(missing) } }
}

val os: OperatingSystem = OperatingSystem.current()

// -Pmogul.aot=false packages without the cache, as a build on a system without a display must
val aot = providers.gradleProperty("mogul.aot").orNull != "false"

fun AotCacheTask.configure() {
    group = "compose desktop"
    enabled = aot
    packageName = compose.desktop.application.nativeDistributions.packageName!!
    seconds = providers.gradleProperty("mogul.aot.seconds").map { it.toInt() }.orElse(15)
    keychain = providers.gradleProperty("compose.desktop.mac.signing.keychain")
}

val trainAppAotCache = tasks.register<TrainAppAotCache>("trainAppAotCache") {
    description = "Trains the AOT cache of the app runDistributable runs"
    configure()
    val createDistributable = tasks.named<AbstractJPackageTask>("createDistributable")
    dependsOn(createDistributable)
    distribution = createDistributable.flatMap { it.destinationDir }
}

val trainDmgAotCache = tasks.register<TrainDmgAotCache>("trainDmgAotCache") {
    description = "Trains the AOT cache of the app in the .dmg"
    configure()
    // as packageDmg, which finalizes in this, does only on its own system
    enabled = enabled && os.isMacOsX
    installers = tasks.named<AbstractJPackageTask>("packageDmg").flatMap { it.destinationDir }
}

val trainDebAotCache = tasks.register<TrainDebAotCache>("trainDebAotCache") {
    description = "Trains the AOT cache of the app in the .deb"
    configure()
    enabled = enabled && os.isLinux
    installers = tasks.named<AbstractJPackageTask>("packageDeb").flatMap { it.destinationDir }
}

// every installer leaves the build with its cache in it (but the .msi). compose makes these
// tasks late, so they're matched as they come
tasks.matching { it.name == "runDistributable" }.configureEach { dependsOn(trainAppAotCache) }
tasks.matching { it.name == "packageDmg" }.configureEach { finalizedBy(trainDmgAotCache) }
tasks.matching { it.name == "packageDeb" }.configureEach { finalizedBy(trainDebAotCache) }
