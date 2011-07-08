grails.project.class.dir = 'target/classes'
grails.project.test.class.dir = 'target/test-classes'
grails.project.test.reports.dir = 'target/test-reports'
grails.project.docs.output.dir = 'docs' // for backwards-compatibility, the docs are checked into gh-pages branch

grails.project.dependency.resolution = {

	inherits 'global'
	log 'warn'

	repositories {
		grailsPlugins()
		grailsHome()
		grailsCentral()
		mavenRepo 'http://repo.grails.org/grails/core'
	}

	dependencies {
		String tomcatVersion = '7.0.16'

		runtime('org.apache.tomcat:tomcat-catalina-ant:' + tomcatVersion) {
			transitive = false
		}
		runtime('org.apache.tomcat.embed:tomcat-embed-core:' + tomcatVersion) {
			transitive = false
		}
		runtime('org.apache.tomcat.embed:tomcat-embed-jasper:' + tomcatVersion) {
			transitive = false
		}
		runtime('org.apache.tomcat.embed:tomcat-embed-logging-log4j:' + tomcatVersion) {
			transitive = false
		}
		runtime('org.eclipse.jdt.core.compiler:ecj:3.6.2') {
			transitive = false
		}
	}
}
