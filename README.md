[![Maven](https://img.shields.io/maven-central/v/org.primefaces.extensions/resources-optimizer-maven-plugin.svg)](https://repo1.maven.org/maven2/org/primefaces/extensions/resources-optimizer-maven-plugin/)
[![License](http://img.shields.io/:license-apache-yellow.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Discord Chat](https://img.shields.io/badge/chat-discord-7289da)](https://discord.gg/gzKFYnpmCY)
[![Actions Status](https://github.com/primefaces-extensions/resources-optimizer-maven-plugin/workflows/Java%20CI/badge.svg)](https://github.com/primefaces-extensions/resources-optimizer-maven-plugin/actions)
[![Stackoverflow](https://img.shields.io/badge/StackOverflow-primefaces-chocolate.svg)](https://stackoverflow.com/questions/tagged/primefaces-extensions)

Resources Optimizer Plugin
================================

[![PrimeFaces Extensions Logo](http://primefaces-extensions.github.io/reports/images/title.png)](https://www.primefaces.org/showcase-ext/)

Maven plugin for web resource optimization of JS/CSS including:
- compressing Javascript
- transpiling Javascript from one version to another (e.g. ECMASCRIPT3 to ECMASCRIPT2015)
- source map generation for Javascript
- compressing and merging CSS
- converting images to base64 encoded data-uri's embedded in your CSS
- ...and more

See [Wiki][Wiki] documentation for the configuration and usage of the plugin. 

[Wiki]: https://github.com/primefaces-extensions/primefaces-extensions.github.com/wiki/Maven-plugin-for-web-resource-optimization

## JDK 8
Plugin version 2.4.1 is the last to support JDK8 because of Google Closure Compiler support.

## JDK 11+
Plugin version 2.5.0+ is for JDK11 only
