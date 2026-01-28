# 

Archives containing JAR files are available as [releases](https://github.com/intisy/docker-java/releases).

## What is docker-java?

Docker-java provides a standalone Docker server for Java.

## Usage in private projects

 * Maven (inside the  file)
```xml
  <repository>
      <id>github</id>
      <url>https://maven.pkg.github.com/intisy/docker-java</url>
      <snapshots><enabled>true</enabled></snapshots>
  </repository>
  <dependency>
      <groupId>io.github.intisy</groupId>
      <artifactId>docker-java</artifactId>
      <version>1.2.4</version>
  </dependency>
```

 * Maven (inside the  file)
```xml
  <servers>
      <server>
          <id>github</id>
          <username>your-username</username>
          <password>your-access-token</password>
      </server>
  </servers>
```

 * Gradle (inside the  or  file)
```groovy
  repositories {
      maven {
          url "https://maven.pkg.github.com/intisy/docker-java"
          credentials {
              username = "<your-username>"
              password = "<your-access-token>"
          }
      }
  }
  dependencies {
      implementation 'io.github.intisy:docker-java:1.2.4'
  }
```

## Usage in public projects

 * Gradle (inside the  or  file)
```groovy
  plugins {
      id "io.github.intisy.github-gradle" version "1.3.7"
  }
  dependencies {
      githubImplementation "intisy:docker-java:1.2.4"
  }
```

Once you have it installed you can use it like so:

```
DockerProvider dockerProvider = DockerProvider.get();
dockerProvider.ensureInstalled();
dockerProvider.start();
DockerClient dockerClient = dockerProvider.getClient();
```

Currently supported setups:
  * [x] - Linux root
  * [x] - Linux rootless
  * [x] - Windows Administrator 
  * [x] - Windows non-Administrator 
  * [x] - macOS root 
  * [x] - macOS rootless

## License

[![Apache License 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
