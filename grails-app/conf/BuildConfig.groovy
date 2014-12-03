grails.project.work.dir = 'target'
grails.project.docs.output.dir = 'docs/manual' // for backwards-compatibility, the docs are checked into gh-pages branch

grails.project.dependency.resolver = 'maven'
grails.project.dependency.resolution = {

	inherits 'global'
	log 'warn'

	repositories {
		grailsCentral()
		mavenLocal()
		mavenCentral()
	}

	dependencies {
		String jettyVersion = '7.6.0.v20120127'
		compile 'org.eclipse.jetty.aggregate:jetty-all:' + jettyVersion, {
			export = false
		}

		String tomcatVersion = '8.0.15'
		compile "org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion", {
			export = false
		}
	}

	plugins {
		build ':release:3.0.1', ':rest-client-builder:2.0.3', {
			export = false
		}
	}
}
