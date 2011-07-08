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

	File workDir = new File(grailsSettings.projectTargetDir, 'standalone-temp-' + System.currentTimeMillis())
	if (!workDir.deleteDir()) {
		event('StatusError', ["Unable to delete $workDir"])
		return
	}
	if (!workDir.mkdirs()) {
		event('StatusError', ["Unable to create $workDir"])
		return
	}

	File warfile = buildWar(workDir)
	buildJar workDir

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

buildJar = { File workDir ->
	for (clazz in [DeployTask, Engine, JspC, LogFactory, JDTCompilerAdapter]) {
		if (!unpackContainingJar(clazz, workDir)) {
			return false
		}
	}

	ant.javac srcdir: new File(standalonePluginDir, 'src/java'),
	          destdir: workDir,
	          source: '1.5',
	          target: '1.5'

	ant.jar(basedir: workDir, destfile: new File(workDir.parentFile, 'standalone.jar')) {
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
