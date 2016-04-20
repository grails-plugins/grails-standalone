/* Copyright 2011-2016 the original author or authors.
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
package grails.plugin.standalone

import junit.framework.TestCase
import spock.lang.Specification

/**
 * Unit tests for AbstractLauncher.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@SuppressWarnings('unused')
class AbstractLauncherSpec extends Specification {

	private AbstractLauncher launcher = createLauncher([])

	void testExtractWar() {
		given:
		File jar = new File(TestCase.protectionDomain.codeSource.location.toString() - 'file:')

		when:
		File extractionDir = launcher.extractWar(new FileInputStream(jar),
			File.createTempFile('embedded', '.war', new File(System.getProperty('java.io.tmpdir'))).absoluteFile)
		File classFile = new File(extractionDir, 'junit/framework/TestCase.class')

		then:
		classFile.exists()

		cleanup:
		extractionDir?.deleteDir()
	}

	void testHasLength() {
		expect:
		!launcher.hasLength(null)
		!launcher.hasLength('')
		!launcher.hasLength(' ')
		launcher.hasLength 'x'
		launcher.hasLength ' x'
		launcher.hasLength 'x '
	}

	void testArgsToMapNull() {
		when:
		launcher.argsToMap null

		then:
		noExceptionThrown()
	}

	void testArgsToMapNone() {
		when:
		def map = launcher.argsToMap([] as String[])

		then:
		!map
	}

	void testArgsToMapNoEquals() {
		when:
		def map = launcher.argsToMap(['host=1', 'context=bar', 'baz'] as String[])

		then:
		map.size() == 2
		map.host == '1'
		map.context == 'bar'
	}

	void testArgsToMap() {
		when:
		def map = launcher.argsToMap(['host=1', 'context=bar'] as String[])

		then:
		map.size()  ==  2
		map.host    == '1'
		map.context == 'bar'
	}

	void testGetArg() {
		when:
		launcher = createLauncher(['port=1', 'context=file.separator'])

		then:
		launcher.getArg('port')           == '1'
		launcher.getArg('missing')        == null
		launcher.getArg('missing', 'def') == 'def'
		launcher.getArg('context')        == File.separator
	}

	void testGetIntArg() {
		when:
		launcher = createLauncher(['port=1', 'context=bar', 'fs=file.separator'])

		then:
		launcher.getIntArg('port', 42) == 1
		launcher.getIntArg('x2', 2)    == 2
		launcher.getIntArg('foo', 3)   == 3
	}

	void testGetBooleanArg() {
		when:
		launcher = createLauncher(['enableClientAuth=TRUE', 'enableCompression=FALSE', 'nio=true', 'tomcat.nio=false'])

		then:
		launcher.getBooleanArg 'enableClientAuth', false
		launcher.getBooleanArg 'nio', false
		launcher.getBooleanArg 'm1', true

		!launcher.getBooleanArg('tomcat.nio', true)
		!launcher.getBooleanArg('enableCompression', true)
		!launcher.getBooleanArg('m1', false)
	}

	private File createTempDir() {
		File tempDir = new File(System.getProperty('java.io.tmpdir'))
		int index = 1
		File dir

		while (true) {
			dir = new File(tempDir, 'standalonetest' + index)
			if (!dir.exists()) {
				assert dir.mkdirs()
				return dir
			}
			index++
		}
	}

	private AbstractLauncher createLauncher(List<String> args) {
		new AbstractLauncher(args as String[]) {
			protected void start(File exploded) {}
		}
	}
}
