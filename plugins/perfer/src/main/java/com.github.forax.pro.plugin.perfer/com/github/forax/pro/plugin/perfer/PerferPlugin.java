package com.github.forax.pro.plugin.perfer;

import static com.github.forax.pro.api.MutableConfig.derive;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;

import com.github.forax.pro.api.Config;
import com.github.forax.pro.api.MutableConfig;
import com.github.forax.pro.api.Plugin;
import com.github.forax.pro.api.WatcherRegistry;
import com.github.forax.pro.api.helper.ProConf;
import com.github.forax.pro.helper.Log;
import com.github.forax.pro.helper.ModuleHelper;
import com.github.forax.pro.helper.Platform;
import com.github.forax.pro.helper.util.StableList;

public class PerferPlugin implements Plugin {
  @Override
  public String name() {
    return "perfer";
  }

  @Override
  public void init(MutableConfig config) {
    config.getOrUpdate(name(), PerferConf.class);
  }
  
  @Override
  public void configure(MutableConfig config) {
    PerferConf perfer = config.getOrUpdate(name(), PerferConf.class);
    ConventionFacade convention = config.getOrThrow("convention", ConventionFacade.class); 
    
    derive(perfer, PerferConf::moduleArtifactTestPath, convention, ConventionFacade::javaModuleArtifactTestPath);
    derive(perfer, PerferConf::moduleDependencyPath, convention, ConventionFacade::javaModuleDependencyPath);
    
    derive(perfer, PerferConf::javaCommand, convention,
        c -> c.javaHome().resolve("bin").resolve(Platform.current().javaExecutableName()));
  }
  
  @Override
  public void watch(Config config, WatcherRegistry registry) {
    PerferConf jmher = config.getOrThrow(name(), PerferConf.class);
    jmher.moduleArtifactTestPath().forEach(registry::watch);
    jmher.moduleDependencyPath().forEach(registry::watch);
  }
  
  @Override
  public int execute(Config config) throws IOException {
    Log log = Log.create(name(), config.getOrThrow("pro", ProConf.class).loglevel());
    PerferConf jmher = config.getOrThrow(name(), PerferConf.class);
    log.debug(config, conf -> "config " + jmher);
    
    ModuleFinder finder = ModuleFinder.of(jmher.moduleArtifactTestPath());
    int exitCode = finder.findAll().stream()
        .flatMap(ref -> ref.location().map(Paths::get).stream())
        .reduce(0, (acc, testPath) -> acc | execute(jmher, testPath), (r1, r2) -> r1 | r2);
    
    return exitCode;
  }
  
  private static int execute(PerferConf perfer, Path testPath) {
    ModuleReference moduleReference = ModuleHelper.getOnlyModule(testPath);
    String moduleName = moduleReference.descriptor().name();
    
    try(ModuleReader reader = moduleReference.open()) {
      Optional<InputStream> input = reader.open("META-INF/BenchmarkList");
      if (!input.isPresent()) {
        return 0;
      }
      
      Collection<BenchmarkListEntry> list = BenchmarkList.readBenchmarkList(input.get());
      Set<String> classNames = list.stream().map(BenchmarkListEntry::getUserClassQName).collect(Collectors.toSet());
      
      for(String className: classNames) {
        int exitCode = executeClass(perfer, moduleName, className);
        if (exitCode != 0) {
          return exitCode;
        }
      }
      return 0;
      
    } catch (IOException e) {
      e.printStackTrace(); // FIXME
      return 1;
    }
  }
  
  private static int executeClass(PerferConf perfer, String moduleName, String className) {
      List<Path> modulePath = StableList.<Path>of()
          .append(perfer.moduleArtifactTestPath())
          .appendAll(perfer.moduleDependencyPath());
      try {
        Process process = new ProcessBuilder(StableList.of(perfer.javaCommand().toString())
            .appendAll(StableList.of(
                "--module-path", modulePath.stream().map(Path::toString).collect(joining(":")),
                "-m", moduleName + '/' + className)))
            .redirectErrorStream(true)
            .start();
          
          process.getInputStream().transferTo(System.out);
        
        return process.waitFor();
      } catch (InterruptedException | IOException e) {
        return 1; // FIXME
      }
  }
}
