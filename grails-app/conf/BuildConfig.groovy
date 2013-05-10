grails.project.work.dir = 'target'
grails.project.docs.output.dir = 'docs/manual' // for backwards-compatibility, the docs are checked into gh-pages branch

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
	}

	plugins {
		build(':release:2.2.1', ':rest-client-builder:1.0.3') {
			export = false
		}
	}
}
