[![pro build status](https://api.travis-ci.org/forax/pro.svg?branch=master)](https://travis-ci.org/forax/pro)
# pro
A Java 9 compatible build tool

> No need to be a maven to be able to use a build tool 


# rationale
With the introduction of modules in Java 9, creating modules/jars is easier and
new applications will tend to have many more, smaller modules than before.
The build model of Maven is not well suited to describe this new world.


# principles

  - **pro**grammatic API first
  - use [convention](https://github.com/forax/pro/blob/master/src/main/java/com.github.forax.pro.plugin.convention/com/github/forax/pro/plugin/convention/ConventionPlugin.java#L17) over configuration
  - stateless [plugins](https://github.com/forax/pro/blob/master/src/main/java/com.github.forax.pro.api/com/github/forax/pro/api/Plugin.java) 
  - separate configuration time where configuration is [mutable](https://github.com/forax/pro/blob/master/src/main/java/com.github.forax.pro.api/com/github/forax/pro/api/MutableConfig.java) and build time where configuration is [immutable](https://github.com/forax/pro/blob/master/src/main/java/com.github.forax.pro.api/com/github/forax/pro/api/Config.java)
  - external dependencies are in plain sight


# architectures
  

# demo
There is a small demo in the github project [pro-demo](https://github.com/forax/pro-demo/).

# build instructions
To compile and build pro, run:
```
build.sh
```
pro will bootstrap itself.

To build pro you need the [jdk10](http://jdk.java.net/10/) or a more recent version,
you may have to change the value of the variable JAVA_HOME at the start of the script build.sh.

Once built, you have an image of the tool in target/pro.
This image embeds its own small JDK: no need to install anything Java-related to be able to build your application.
Obviously, you will need a JDK to run your application. 
