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
class StandaloneGrailsPlugin {

	String version = '1.0'
	String grailsVersion = '1.3 > *'
	def scopes = [excludes: 'war']

	String author = 'Burt Beckwith'
	String authorEmail = 'beckwithb@vmware.com'
	String title = 'Standalone App Runner'
	String description = 'Runs a Grails application as a JAR file with an embedded Tomcat server'
	String documentation = 'http://grails.org/plugin/standalone'

	String license = 'APACHE'
	def organization = [ name: 'SpringSource', url: 'http://www.springsource.org/' ]
	def developers = [
		 [ name: 'Burt Beckwith', email: 'beckwithb@vmware.com' ] ]
	def issueManagement = [ system: 'JIRA', url: 'http://jira.grails.org/browse/GPSTANDALONE' ]
	def scm = [ url: 'https://github.com/grails-plugins/grails-standalone' ]
}
