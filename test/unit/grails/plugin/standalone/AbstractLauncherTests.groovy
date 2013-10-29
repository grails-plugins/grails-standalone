package grails.plugin.standalone

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;

import junit.framework.TestCase

class AbstractLauncherTests extends GroovyTestCase {

	private AbstractLauncher launcher = createLauncher([])

	void testExtractWar() {
		File jar = new File(TestCase.protectionDomain.codeSource.location.toString() - 'file:')

		File extractionDir
		try {
			extractionDir = launcher.extractWar(new FileInputStream(jar),
				File.createTempFile("embedded", ".war", new File(System.getProperty('java.io.tmpdir'))).getAbsoluteFile())
			File classFile = new File(extractionDir, 'junit/framework/TestCase.class')
			assert classFile.exists()
		}
		finally {
			extractionDir.deleteDir()
		}
	}

	void testHasLength() {
		assertFalse launcher.hasLength(null)
		assertFalse launcher.hasLength('')
		assertFalse launcher.hasLength(' ')
		assertTrue launcher.hasLength('x')
		assertTrue launcher.hasLength(' x')
		assertTrue launcher.hasLength('x ')
	}

	void testArgsToMapNull() {
		shouldFail(NullPointerException) {
			launcher.argsToMap null
		}
	}

	void testArgsToMapNone() {
		def map = launcher.argsToMap([] as String[])
		assert map.isEmpty()
	}

	void testArgsToMapNoEquals() {
		def map = launcher.argsToMap(['host=1', 'context=bar', 'baz'] as String[])
		assert 2 == map.size()
		assert '1' == map.host
		assert 'bar' == map.context
	}

	void testArgsToMap() {
		def map = launcher.argsToMap(['host=1', 'context=bar'] as String[])
		assert 2 == map.size()
		assert '1' == map.host
		assert 'bar' == map.context
	}

	void testGetArg() {
		launcher = createLauncher(['port=1', 'context=file.separator'])
		assert '1' == launcher.getArg('port')
		assertNull launcher.getArg('missing')
		assert 'def' == launcher.getArg('missing', 'def')
		assert File.separator == launcher.getArg('context')
	}

	void testGetIntArg() {
		launcher = createLauncher(['port=1', 'context=bar', 'fs=file.separator'])
		assert 1 == launcher.getIntArg('port', 42)
		assert 2 == launcher.getIntArg('x2', 2)
		assert 3 == launcher.getIntArg('foo', 3)
	}

	void testGetBooleanArg() {
		launcher = createLauncher(['enableClientAuth=TRUE', 'enableCompression=FALSE', 'nio=true', 'tomcat.nio=false'])
		assert launcher.getBooleanArg('enableClientAuth', false)
		assert !launcher.getBooleanArg('enableCompression', true)
		assert launcher.getBooleanArg('nio', false)
		assert !launcher.getBooleanArg('tomcat.nio', true)
		assert launcher.getBooleanArg('m1', true)
		assert !launcher.getBooleanArg('m1', false)
	}

	void testDeleteDirMissing() {
		File dir = new File('not a dir')
		assert !dir.exists()
		assert launcher.deleteDir(dir)
	}

	void testDeleteDirFile() {
		File file = File.createTempFile("test", ".txt")
		file.deleteOnExit()

		assert file.exists()
		assert !launcher.deleteDir(file)
		assert file.exists()
	}

	void testDeleteDirEmpty() {
		File tempDir = new File(System.getProperty('java.io.tmpdir'))

		int index = 1
		File dir
		try {
			dir = createTempDir()
			assert launcher.deleteDir(dir)
			assert !dir.exists()
		}
		finally {
			dir.deleteDir()
		}
	}

	void testDeleteDirNotEmpty() {
		File tempDir = new File(System.getProperty('java.io.tmpdir'))

		int index = 1
		File dir
		try {
			dir = createTempDir()
			File sub = new File(dir, 'sub')
			sub.mkdirs()
			assert new File(sub, 'foo').createNewFile()
			assert launcher.deleteDir(dir)
			assert !dir.exists()
		}
		finally {
			dir.deleteDir()
		}
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
		return new AbstractLauncher(args as String[]) {
			protected void start(File exploded) {}
		}
	}
}
