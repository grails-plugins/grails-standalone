/* Copyright 2011-2012 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import grails.util.BuildSettings
import grails.util.GrailsUtil

import org.apache.ivy.core.report.ResolveReport
import org.codehaus.groovy.grails.resolve.IvyDependencyManager

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */

includeTargets << grailsScript('_GrailsWar')

target(buildStandalone: 'Build a standalone app with embedded server') {
	depends configureProxy, compile, createConfig, loadPlugins

	try {
		if ('development'.equals(grailsEnv) && !argsMap.warfile) {
			event 'StatusUpdate', ["You're running in the development environment but haven't specified a war file, so one will be built with development settings."]
		}

		File workDir = new File(grailsSettings.projectTargetDir, 'standalone-temp-' + System.currentTimeMillis()).absoluteFile
		if (!workDir.deleteDir()) {
			event 'StatusError', ["Unable to delete $workDir"]
			return
		}
		if (!workDir.mkdirs()) {
			event 'StatusError', ["Unable to create $workDir"]
			return
		}

		String jarname = argsMap.params[0]
		File jar = jarname ? new File(jarname).absoluteFile : new File(workDir.parentFile, 'standalone-' + grailsAppVersion + '.jar').absoluteFile

		File warfile
		if (argsMap.warfile) {
			warfile = new File(argsMap.warfile).absoluteFile
			if (warfile.exists()) {
				println "Using war file $argsMap.warfile"
				if (!buildJar(workDir, jar, warfile)) {
					return
				}
			}
			else {
				errorAndDie "War file $argsMap.warfile not found"
			}
		}
		else {
			warfile = buildWar(workDir)
			if (!buildJar(workDir, jar)) {
				return
			}
		}

		if (!workDir.deleteDir()) {
			event 'StatusError', ["Unable to delete $workDir"]
		}

		event 'StatusUpdate', ["Built $jar.path"]
	}
	catch (e) {
		GrailsUtil.deepSanitize e
		throw e
	}
}

buildWar = { File workDir ->
	File warfile = new File(workDir, 'embedded.war').absoluteFile
	warfile.deleteOnExit()

	argsMap.params.clear()
	argsMap.params << warfile.path
	war()
	removeTomcatJarsFromWar workDir, warfile

	warfile
}

buildJar = { File workDir, File jar, File warfile = null ->

	List<String> dependencyJars = resolveJars(config.grails.plugin.standalone)

	ant.path(id: 'standalone.cp') { dependencyJars.each { pathelement(path: it) } }

	// compile Launcher.java so it's directly in the JAR
	ant.javac srcdir: new File(standalonePluginDir, 'src/runtime'), destdir: workDir,
	          debug: true, source: '1.5', target: '1.5', listfiles: true,
	          classpathref: 'standalone.cp', includeAntRuntime: false

	for (jarPath in dependencyJars) {
		ant.unjar src: jarPath, dest: workDir
	}

	jar.parentFile.mkdirs()
	ant.jar(destfile: jar) {
		fileset dir: workDir
		if (warfile) {
			zipfileset file: warfile, fullpath: 'embedded.war'
		}
		manifest {
			attribute name: 'Main-Class', value: 'grails.plugin.standalone.Launcher'
		}
	}

	true
}

removeTomcatJarsFromWar = { File workDir, File warfile ->
	def expandedDir = new File(workDir, 'expanded').absoluteFile
	ant.unzip src: warfile, dest: expandedDir
	for (file in new File(expandedDir, 'WEB-INF/lib').listFiles()) {
		if (file.name.startsWith('tomcat-') && !file.name.contains('pool')) {
			file.delete()
		}
	}
	warfile.delete()
	ant.zip basedir: expandedDir, destfile: warfile
	expandedDir.deleteDir()
}

resolveJars = { standaloneConfig ->

	String tomcatVersion = '7.0.29'

	def deps = ['org.eclipse.jdt.core.compiler:ecj:3.7.1']

	for (name in ['tomcat-annotations-api', 'tomcat-api', 'tomcat-catalina-ant', 'tomcat-catalina',
	              'tomcat-coyote', 'tomcat-juli', 'tomcat-servlet-api', 'tomcat-util']) {
		deps << 'org.apache.tomcat:' + name + ':' + tomcatVersion
	}

	for (name in ['tomcat-embed-core', 'tomcat-embed-jasper', 'tomcat-embed-logging-juli', 'tomcat-embed-logging-log4j']) {
		deps << 'org.apache.tomcat.embed:' + name + ':' + tomcatVersion
	}

	if (standaloneConfig.extraDependencies instanceof Collection) {
		deps.addAll standaloneConfig.extraDependencies
	}

	def manager = new IvyDependencyManager('standalone', '0.1', new BuildSettings())
	manager.parseDependencies {
		log standaloneConfig.ivyLogLevel ?: 'warn'
		repositories {
			mavenLocal()
			mavenCentral()
		}
		dependencies {
			compile(*deps) {
				transitive = false
			}
		}
	}

	ResolveReport report = manager.resolveDependencies()
	if (report.hasError()) {
		// TODO
		return null
	}

	def paths = []
	for (File file in report.allArtifactsReports.localFile) {
		if (file) paths << file.path
	}

	paths
}

setDefaultTarget buildStandalone
