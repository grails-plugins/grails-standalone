/* Copyright 2011 SpringSource
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

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */

import org.apache.catalina.Engine // tomcat-catalina-ant-7.0.16.jar
import org.apache.catalina.ant.DeployTask // tomcat-embed-core-7.0.16.jar
import org.apache.jasper.JspC // tomcat-embed-jasper-7.0.16.jar
import org.apache.juli.logging.LogFactory // tomcat-embed-logging-log4j-7.0.16.jar
import org.eclipse.jdt.core.JDTCompilerAdapter // ecj-3.6.2.jar

includeTargets << grailsScript('_GrailsWar')

target(buildStandalone: 'Build a standalone app with embedded server') {
	depends configureProxy, compile, createConfig, loadPlugins

	if ('development'.equals(grailsEnv) && !argsMap.warfile) {
		event('StatusUpdate', ["You're running in the development environment but haven't specified a war file, so one will be built with development settings."])
	}

	File workDir = new File(grailsSettings.projectTargetDir, 'standalone-temp-' + System.currentTimeMillis())
	if (!workDir.deleteDir()) {
		event('StatusError', ["Unable to delete $workDir"])
		return
	}
	if (!workDir.mkdirs()) {
		event('StatusError', ["Unable to create $workDir"])
		return
	}

	String jarname = argsMap.params[0]

	File warfile
	if (argsMap.warfile) {
		warfile = new File(argsMap.warfile)
		if (warfile.exists()) {
			println "Using war file $argsMap.warfile"
		}
		else {
			errorAndDie "War file $argsMap.warfile not found"
		}
	}
	else {
		warfile = buildWar(workDir)
	}

	buildJar workDir, jarname

	if (!workDir.deleteDir()) {
		event('StatusError', ["Unable to delete $workDir"])
	}
}

buildWar = { File workDir ->
	File warfile = new File(workDir, 'embedded.war')
	warfile.deleteOnExit()

	argsMap.params.clear()
	argsMap.params << warfile.path
	war()
	removeTomcatJarsFromWar workDir, warfile

	warfile
}

buildJar = { File workDir, String jarPath ->
	for (clazz in [DeployTask, Engine, JspC, LogFactory, JDTCompilerAdapter]) {
		if (!unpackContainingJar(clazz, workDir)) {
			return false
		}
	}

	// compile Launcher.java so it's directly in the JAR
	ant.javac srcdir: new File(standalonePluginDir, 'src/java'),
	          destdir: workDir,
	          debug: true,
	          source: '1.5',
	          target: '1.5'

	File jar = jarPath ? new File(jarPath) : new File(workDir.parentFile, 'standalone.jar')
	jar.parentFile.mkdirs()
	ant.jar(basedir: workDir, destfile: jar) {
		manifest {
			attribute name: 'Main-Class', value: 'grails.plugin.standalone.Launcher'
		}
	}

	true
}

unpackContainingJar = { Class clazz, File workDir ->
	File jar = new File(clazz.protectionDomain.codeSource.location.toURI())
	if (!jar.exists()) {
		event('StatusError', ["Jar $jar not found"])
		return false
	}

	ant.unjar src: jar, dest: workDir
	true
}

removeTomcatJarsFromWar = { File workDir, File warfile ->
	def expandedDir = new File(workDir, 'expanded')
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

setDefaultTarget buildStandalone
