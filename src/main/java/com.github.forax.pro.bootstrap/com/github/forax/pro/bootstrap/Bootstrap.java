package com.github.forax.pro.bootstrap;

import static com.github.forax.pro.Pro.list;
import static com.github.forax.pro.Pro.local;
import static com.github.forax.pro.Pro.location;
import static com.github.forax.pro.Pro.path;
import static com.github.forax.pro.Pro.run;
import static com.github.forax.pro.Pro.set;
import static com.github.forax.pro.Pro.uri;
import static com.github.forax.pro.helper.FileHelper.deleteAllFiles;
import static com.github.forax.pro.helper.FileHelper.walkAndFindCounterpart;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import com.github.forax.pro.helper.ModuleHelper;

public class Bootstrap {

  private static int jdkVersion() {
    var major = Runtime.version().feature();
    return Math.min(major, 10);  //FIXME --release doesn't work yet with the jdk 11
  }

  public static void main(String[] args) throws IOException {
    set("pro.loglevel", "verbose");
    set("pro.exitOnError", true);

    // set("compiler.lint", "exports,module");
    set("compiler.lint", "all,-varargs,-overloads");
    set("compiler.release", jdkVersion());

    var version = "0." + jdkVersion();
    set("packager.modules", list(
        "com.github.forax.pro@" + version,
        "com.github.forax.pro.aether@" + version,
        "com.github.forax.pro.ather.fakeguava@" + version,
        "com.github.forax.pro.api@" + version,
        "com.github.forax.pro.bootstrap@" + version,
        "com.github.forax.pro.builder@" + version,
        "com.github.forax.pro.helper@" + version,
        "com.github.forax.pro.main@" + version + "/com.github.forax.pro.main.Main",
        "com.github.forax.pro.plugin.convention@" + version,
        "com.github.forax.pro.plugin.resolver@" + version,
        "com.github.forax.pro.plugin.modulefixer@" + version,
        "com.github.forax.pro.plugin.compiler@" + version,
        "com.github.forax.pro.plugin.docer@" + version,
        "com.github.forax.pro.plugin.packager@" + version,
        "com.github.forax.pro.plugin.linker@" + version,
        "com.github.forax.pro.plugin.runner@" + version,
        "com.github.forax.pro.plugin.tester@" + version,
        "com.github.forax.pro.plugin.jmher@" + version,
        "com.github.forax.pro.plugin.uberpackager@" + version,
        "com.github.forax.pro.plugin.bootstrap@" + version + "/com.github.forax.pro.bootstrap.Bootstrap",
        "com.github.forax.pro.bootstrap.genbuilder@" + version + "/com.github.forax.pro.bootstrap.genbuilder.GenBuilder",
        "com.github.forax.pro.ubermain@" + version,
        "com.github.forax.pro.uberbooter@" + version,
        "com.github.forax.pro.daemon@" + version,
        "com.github.forax.pro.daemon.imp@" + version
        ));

    //set("modulefixer.force", true);
    set("modulefixer.additionalRequires", list(
        "maven.aether.provider=commons.lang",
        "maven.aether.provider=com.github.forax.pro.aether.fakeguava",
        "maven.aether.provider=plexus.utils",
        "maven.builder.support=commons.lang",
        "maven.modelfat=commons.lang",
        "aether.impl=aether.util",
        "aether.transport.http=aether.util",
        "aether.connector.basic=aether.util"
        ));

    set("docer.quiet", true);
    set("docer.link", uri("https://docs.oracle.com/javase/10/docs/api/"));

    set("linker.includeSystemJMODs", true);
    set("linker.launchers", list(
        "pro=com.github.forax.pro.main/com.github.forax.pro.main.Main"
        ));
    set("linker.rootModules", list(
        "com.github.forax.pro.main",
        "com.github.forax.pro.plugin.convention",
        "com.github.forax.pro.plugin.resolver",
        "com.github.forax.pro.plugin.modulefixer",
        "com.github.forax.pro.plugin.compiler",
        "com.github.forax.pro.plugin.docer",
        "com.github.forax.pro.plugin.packager",
        "com.github.forax.pro.plugin.linker",
        "com.github.forax.pro.plugin.uberpackager",
        "com.github.forax.pro.uberbooter",            // needed by ubermain
        "com.github.forax.pro.daemon.imp"
        )                                             // then add all system modules
        .appendAll(ModuleHelper.systemModulesFinder().findAll().stream()
                  .map(ref -> ref.descriptor().name())
                  .collect(Collectors.toSet())));

    run("modulefixer", "compiler", "docer", "packager");

    compileAndPackagePlugin("runner", list("resolver", "modulefixer", "compiler", "packager"), () -> { /* empty */});
    compileAndPackagePlugin("tester", list("resolver", "modulefixer", "compiler", "packager"), () -> {
      var junitPlatformVersion = "1.1.0";
      var junitJupiterVersion = "5.1.0";
      var opentest4jVersion = "1.0.0";
      var apiGuardianVersion = "1.0.0";
      set("resolver.dependencies", list(
          // "API"
          "org.opentest4j=org.opentest4j:opentest4j:" + opentest4jVersion,
          "org.apiguardian=org.apiguardian:apiguardian-api:" + apiGuardianVersion,
          "org.junit.platform.commons=org.junit.platform:junit-platform-commons:" + junitPlatformVersion,
          "org.junit.jupiter.api=org.junit.jupiter:junit-jupiter-api:" + junitJupiterVersion,
          // "Launcher + Engine"
          "org.junit.platform.engine=org.junit.platform:junit-platform-engine:" + junitPlatformVersion,
          "org.junit.platform.launcher=org.junit.platform:junit-platform-launcher:" + junitPlatformVersion,
          "org.junit.jupiter.engine=org.junit.jupiter:junit-jupiter-engine:" + junitJupiterVersion
      ));
    });
    compileAndPackagePlugin("perfer", list("resolver", "modulefixer", "compiler", "packager"), () -> {
      var jmhVersion = "1.20";
      var commonMath3Version = "3.6.1";
      var joptSimpleVersion = "5.0.4";
      set("resolver.dependencies", list(
          // "JMH Core"
          "org.openjdk.jmh=org.openjdk.jmh:jmh-core:" + jmhVersion,
          "org.apache.commons.math3=org.apache.commons:commons-math3:" + commonMath3Version,
          "net.sf.jopt-simple=net.sf.jopt-simple:jopt-simple:" + joptSimpleVersion
      ));
    });

    //    compileAndPackagePlugin("formatter", () -> {
    //      set(
    //        "resolver.remoteRepositories",
    //        list(uri("https://oss.sonatype.org/content/repositories/snapshots")));
    //      String gjfVersion = "1.5";
    //      String guavaVersion = "24.1";
    //      String javacShadedVersion = "9+181-r4173-1";
    //      set("resolver.dependencies", list(
    //          // "Google Java Format"
    //          "com.google.googlejavaformat=com.google.googlejavaformat:google-java-format:" +
    // gjfVersion,
    //          "com.google.guava=com.google.guava:guava:" + guavaVersion,
    //          "com.google.errorprone=com.google.errorprone:javac-shaded:" + javacShadedVersion,
    //          "com.google.j2objc=com.google.j2objc:j2objc-annotations:1.1",
    // "org.codehaus.animal.sniffer.annotations=org.codehaus.mojo:animal-sniffer-annotations:1.14"
    //      ));
    //    });
    compileAndPackagePlugin("formatter", list("compiler", "packager"), () -> {
      var gjfVersion = "1.5";
      var base = "https://github.com/google/google-java-format/releases/download/google-java-format";
      download(
          uri(base + "-" + gjfVersion + "/google-java-format-" + gjfVersion + "-all-deps.jar"),
          location("plugins/formatter/libs"));
    });

    run("linker" /*, "uberpackager" */);

    copyPackagedPluginToTargetImage("runner");
    copyPackagedPluginToTargetImage("tester");
    copyPackagedPluginToTargetImage("perfer");
    copyPackagedPluginToTargetImage("formatter");

    // re-generate builders
    //com.github.forax.pro.Pro.update(java.nio.file.Paths.get("target/image/plugins"));
    //com.github.forax.pro.bootstrap.genbuilder.GenBuilder.generate();

    Vanity.postOperations();
  }

  private static void compileAndPackagePlugin(String name, List<String> plugins, Runnable extras) throws IOException {
    deleteAllFiles(location("plugins/" + name + "/target"), false);

    local("plugins/" + name, () -> {
      set("resolver.moduleDependencyPath",
          path("plugins/" + name + "/deps", "target/main/artifact/", "deps"));
      set("compiler.moduleDependencyPath",
          path("plugins/" + name + "/deps", "target/main/artifact/", "deps"));

      extras.run();

      run(plugins);
    });
  }

  private static void copyPackagedPluginToTargetImage(String name) throws IOException {
    createDirectories(location("target/image/plugins/" + name));
    path("plugins/" + name + "/target/main/artifact", "plugins/" + name + "/deps")
        .filter(Files::exists)
        .forEach(
            srcPath ->
                walkAndFindCounterpart(
                    srcPath,
                    location("target/image/plugins/" + name),
                    stream -> stream.filter(p -> p.toString().endsWith(".jar")),
                    Files::copy));
    if (Files.exists(Paths.get("plugins/" + name + "/libs"))) {
      createDirectories(location("target/image/plugins/" + name + "/libs"));
      path("plugins/" + name + "/libs")
          .filter(Files::exists)
          .forEach(
              srcPath ->
                  walkAndFindCounterpart(
                      srcPath,
                      location("target/image/plugins/" + name + "/libs"),
                      stream -> stream.filter(p -> p.toString().endsWith(".jar")),
                      Files::copy));
    }
  }

  private static void download(URI urlSpec, Path targetDirectory) {
    var fileName = Paths.get(urlSpec.getPath()).getFileName();
    var targetFile = targetDirectory.resolve(fileName);
    if (Files.exists(targetFile)) {
      return;
    }
    try { 
      Files.createDirectories(targetDirectory);
      try(var input = Channels.newChannel(urlSpec.toURL().openStream());
          var output = FileChannel.open(targetFile, CREATE_NEW, WRITE)) {
        output.transferFrom(input, 0, Long.MAX_VALUE);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("download failed: url=" + urlSpec + " targetDirectory=" + targetDirectory, e);
    }
  }
}
