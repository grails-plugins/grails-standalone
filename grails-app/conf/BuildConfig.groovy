grails.project.work.dir = 'target'

grails.project.dependency.resolver = 'maven'
grails.project.dependency.resolution = {

	inherits 'global'
	log 'warn'

	repositories {
		mavenLocal()
		grailsCentral()
		mavenCentral()
	}

	dependencies {
		String jettyVersion = '7.6.0.v20120127'
		compile 'org.eclipse.jetty.aggregate:jetty-all:' + jettyVersion, {
			export = false
		}

		String tomcatVersion = '8.0.33'
		compile "org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion", {
			export = false
		}
	}

	plugins {
		build ':release:3.1.2', ':rest-client-builder:2.1.1', {
			export = false
		}
	}
}
