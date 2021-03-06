package com.github.forax.pro.plugin.compiler;

import static com.github.forax.pro.api.MutableConfig.derive;
import static com.github.forax.pro.api.helper.OptionAction.*;
import static com.github.forax.pro.api.helper.OptionAction.actionMaybe;
import static com.github.forax.pro.api.helper.OptionAction.exists;
import static com.github.forax.pro.api.helper.OptionAction.gatherAll;
import static com.github.forax.pro.helper.FileHelper.deleteAllFiles;
import static com.github.forax.pro.helper.FileHelper.pathFilenameEndsWith;
import static com.github.forax.pro.helper.FileHelper.walkIfNecessary;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

import com.github.forax.pro.api.Config;
import com.github.forax.pro.api.MutableConfig;
import com.github.forax.pro.api.Plugin;
import com.github.forax.pro.api.WatcherRegistry;
import com.github.forax.pro.api.helper.CmdLine;
import com.github.forax.pro.api.helper.ProConf;
import com.github.forax.pro.api.helper.OptionAction;
import com.github.forax.pro.helper.FileHelper;
import com.github.forax.pro.helper.Log;
import com.github.forax.pro.helper.ModuleHelper;
import com.github.forax.pro.helper.util.StableList;

public class CompilerPlugin implements Plugin {
  @Override
  public String name() {
    return "compiler";
  }

  @Override
  public void init(MutableConfig config) {
    var compilerConf = config.getOrUpdate(name(), CompilerConf.class);
    compilerConf.release(9);
  }
  
  @Override
  public void configure(MutableConfig config) {
    var compilerConf = config.getOrUpdate(name(), CompilerConf.class);
    var convention = config.getOrThrow("convention", ConventionFacade.class);
    
    // inputs
    derive(compilerConf, CompilerConf::moduleDependencyPath, convention, ConventionFacade::javaModuleDependencyPath);
    derive(compilerConf, CompilerConf::moduleSourcePath, convention, ConventionFacade::javaModuleSourcePath);
    derive(compilerConf, CompilerConf::moduleSourceResourcesPath, convention, ConventionFacade::javaModuleSourceResourcesPath);
    derive(compilerConf, CompilerConf::moduleTestPath, convention, ConventionFacade::javaModuleTestPath);
    derive(compilerConf, CompilerConf::moduleTestResourcesPath, convention, ConventionFacade::javaModuleTestResourcesPath);
    
    // outputs
    derive(compilerConf, CompilerConf::moduleExplodedSourcePath, convention, c -> c.javaModuleExplodedSourcePath().get(0));
    derive(compilerConf, CompilerConf::moduleMergedTestPath, convention, c -> c.javaModuleMergedTestPath().get(0));
    derive(compilerConf, CompilerConf::moduleExplodedTestPath, convention, c -> c.javaModuleExplodedTestPath().get(0));
  }
  
  @Override
  public void watch(Config config, WatcherRegistry registry) {
    var compilerConf = config.getOrThrow(name(), CompilerConf.class);
    compilerConf.moduleDependencyPath().forEach(registry::watch);
    compilerConf.moduleSourcePath().forEach(registry::watch);
    compilerConf.moduleSourceResourcesPath().forEach(registry::watch);
    compilerConf.moduleTestPath().forEach(registry::watch);
    compilerConf.moduleTestResourcesPath().forEach(registry::watch);
  }
  
  static Optional<List<Path>> modulePathOrDependencyPath(Optional<List<Path>> modulePath, List<Path> moduleDependencyPath, List<Path> additionnalPath) {
    return modulePath
             .or(() -> Optional.of(
                    StableList.from(moduleDependencyPath).appendAll(additionnalPath)))
             .map(FileHelper.unchecked(FileHelper::pathFromFilesThatExist))
             .filter(list -> !list.isEmpty());
  }
  
  enum JavacOption {
    RELEASE(action("--release", Javac::release)),
    VERBOSE(exists("-verbose", Javac::verbose)),
    LINT(javac -> javac.lint().map(lint -> line -> line.add("-Xlint:" + lint))),
    RAW_ARGUMENTS(rawValues(Javac::rawArguments)),
    DESTINATION(action("-d", Javac::destination)),
    MODULE_SOURCE_PATH(action("--module-source-path", Javac::moduleSourcePath, File.pathSeparator)),
    ROOT_MODULES(actionMaybe("--add-modules", Javac::rootModules, ",")),
    UPGRADE_MODULE_PATH(actionMaybe("--upgrade-module-path", Javac::upgradeModulePath, File.pathSeparator)),
    MODULE_PATH(actionMaybe("--module-path", Javac::modulePath, File.pathSeparator)),
    ;
    
    final OptionAction<Javac> action;
    
    private JavacOption(OptionAction<Javac> action) {
      this.action = action;
    }
  }
  
  @Override
  public int execute(Config config) throws IOException {
    var log = Log.create(name(), config.getOrThrow("pro", ProConf.class).loglevel());
    log.debug(config, conf -> "config " + config);
    
    var javacTool = ToolProvider.findFirst("javac")
        .orElseThrow(() -> new IllegalStateException("can not find javac"));
    var compiler = config.getOrThrow(name(), CompilerConf.class);
    
    
    var moduleSourceFinder = ModuleHelper.sourceModuleFinders(compiler.moduleSourcePath());
    var errorCode = compile(log, javacTool, compiler,
        compiler.moduleSourcePath(),
        moduleSourceFinder,
        List.of(),
        compiler.moduleSourceResourcesPath(),
        compiler.moduleExplodedSourcePath(),
        "source:");
    if (errorCode != 0) {
      return errorCode;
    }
    var moduleTestPath = FileHelper.pathFromFilesThatExist(compiler.moduleTestPath());
    if (moduleTestPath.isEmpty()) {
      return 0;
    }
    
    var moduleTestFinder = ModuleHelper.sourceModuleFinders(compiler.moduleTestPath());
    if (moduleTestFinder.findAll().isEmpty()) {  // there is no test module-info defined
      log.info(compiler.moduleTestPath(), testPath -> "test: can not find any test modules in " + testPath.stream().map(Path::toString).collect(Collectors.joining(", ")));
      return 0;
    }
    
    var moduleMergedTestPath = compiler.moduleMergedTestPath();
    deleteAllFiles(moduleMergedTestPath, false);
    
    errorCode = merge(moduleSourceFinder, moduleTestFinder, moduleMergedTestPath);
    if (errorCode != 0) {
      return errorCode;
    }
    
    var moduleMergedTestFinder = ModuleHelper.sourceModuleFinder(compiler.moduleMergedTestPath());
    return compile(log, javacTool, compiler,
        List.of(moduleMergedTestPath),
        moduleMergedTestFinder,
        List.of(compiler.moduleExplodedSourcePath()),
        StableList.<Path>of().appendAll(compiler.moduleSourceResourcesPath()).appendAll(compiler.moduleTestResourcesPath()),
        compiler.moduleExplodedTestPath(),
        "test:");
  }

  private static int compile(Log log, ToolProvider javacTool, CompilerConf compiler, List<Path> moduleSourcePath, ModuleFinder moduleFinder, List<Path> additionalSourcePath, List<Path> resourcesPath, Path destination, String pass) throws IOException {
    Optional<List<Path>> modulePath = modulePathOrDependencyPath(compiler.modulePath(),
        compiler.moduleDependencyPath(), additionalSourcePath);
    
    var dependencyFinder = ModuleFinder.compose(
        modulePath
            .stream()
            .flatMap(List::stream)
            .map(ModuleFinder::of)
            .toArray(ModuleFinder[]::new));
    var rootSourceNames = moduleFinder.findAll().stream()
            .map(ref -> ref.descriptor().name())
            .collect(toList());
    if (rootSourceNames.isEmpty()) {
      log.error(moduleSourcePath, sourcePath -> pass + " can not find any modules in " + sourcePath.stream().map(Path::toString).collect(Collectors.joining(", ")));
      return 1; //FIXME
    }
    
    var systemFinder = ModuleHelper.systemModulesFinder();
    
    log.debug(moduleFinder, finder -> pass + " modules " + finder.findAll().stream().map(ref -> ref.descriptor().name()).sorted().collect(Collectors.joining(", ")));
    log.debug(dependencyFinder, finder -> pass + " dependency modules " + finder.findAll().stream().map(ref -> ref.descriptor().name()).sorted().collect(Collectors.joining(", ")));
    log.debug(systemFinder, finder -> pass + " system modules " + finder.findAll().stream().map(ref -> ref.descriptor().name()).sorted().collect(Collectors.joining(", ")));
    
    /*
    Configuration.resolveRequires(ModuleFinder.compose(sourceModuleFinder, dependencyFinder),
        List.of(Layer.boot().configuration()), ModuleFinder.of(), rootNames);
    */
    var resolved = ModuleHelper.resolveOnlyRequires(
        ModuleFinder.compose(moduleFinder, dependencyFinder, systemFinder),
        rootSourceNames,
        (moduleName, dependencyChain) -> {
          log.error(null, __ -> pass + " can not resolve " + moduleName + " from " + dependencyChain);
        });
    if (!resolved) {
      return 1;  //FIXME
    }
    
    deleteAllFiles(destination, false);
    
    var javac = new Javac(compiler.release(), destination, moduleSourcePath);
    compiler.verbose().ifPresent(javac::verbose);
    compiler.lint().ifPresent(javac::lint);
    compiler.rawArguments().ifPresent(javac::rawArguments);
    modulePath.ifPresent(javac::modulePath);
    compiler.upgradeModulePath().ifPresent(javac::upgradeModulePath);
    compiler.rootModules().ifPresent(javac::rootModules);
    
    
    var cmdLine = gatherAll(JavacOption.class, option -> option.action).apply(javac, new CmdLine());
    var files = compiler.files().orElseGet(
        () -> walkIfNecessary(moduleSourcePath, pathFilenameEndsWith(".java")));  //FIXME, use rootNames ??
    files.forEach(cmdLine::add);
    var arguments = cmdLine.toArguments();
    log.verbose(files, fs -> OptionAction.toPrettyString(JavacOption.class, option -> option.action).apply(javac, "javac") + "\n" + fs.stream().map(Path::toString).collect(Collectors.joining(" ")));
    
    var errorCode = javacTool.run(System.out, System.err, arguments);
    if (errorCode != 0) {
      return errorCode;
    }
    
    //copy all resources
    for(var resources: resourcesPath) {
      if (Files.exists(resources)) {
        log.debug(null, __ -> "copy " + resources + " to " + destination);
        FileHelper.walkAndFindCounterpart(resources, destination, Function.identity(), (src, dest) -> {
          if (Files.isDirectory(src) && Files.isDirectory(dest)) { // do not overwrite directory
            return;
          }
          Files.copy(src, dest); 
        });
      }
    }
    return 0;
  }
  
  private static int merge(ModuleFinder moduleSourceFinder, ModuleFinder moduleTestFinder,
                           Path moduleMergedTestPath) throws IOException {
    Files.createDirectories(moduleMergedTestPath);
    
    var skipModuleInfoDotJava = FileHelper.pathFilenameEquals("module-info.java").negate();
    
    for(var testRef: moduleTestFinder.findAll()) {
      var moduleName = testRef.descriptor().name();
      var moduleRoot = moduleMergedTestPath.resolve(moduleName);
      
      Predicate<Path> predicate;
      var sourceRefOpt = moduleSourceFinder.find(moduleName);
      if (sourceRefOpt.isPresent()) {
        var sourceRef = sourceRefOpt.get();
        
        var sourcePath = Paths.get(sourceRef.location().get());
        FileHelper.walkAndFindCounterpart(sourcePath, moduleRoot,
            stream -> stream.filter(skipModuleInfoDotJava),
            Files::copy);
        
        var descriptor = ModuleHelper.mergeModuleDescriptor(sourceRef.descriptor(), testRef.descriptor());
        Files.write(moduleRoot.resolve("module-info.java"), List.of(ModuleHelper.moduleDescriptorToSource(descriptor)));
        
        predicate = skipModuleInfoDotJava;
        
      } else {
        predicate = __ -> true;
      }
      
      var testPath = Paths.get(testRef.location().get());
      FileHelper.walkAndFindCounterpart(testPath, moduleRoot, stream -> stream.filter(predicate),
          (srcPath, dstPath) -> {
            if (Files.exists(dstPath) && Files.isDirectory(dstPath)) {
              return;  // skip existing path
            }
            Files.copy(srcPath, dstPath);
          });
    }
    return 0;
  }
}
