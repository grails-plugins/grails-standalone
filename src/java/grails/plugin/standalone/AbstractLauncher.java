/* Copyright 2012-2014 SpringSource
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
package grails.plugin.standalone;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.ServletException;

/**
 * Abstract base class for the Tomcat and Jetty launchers.
 *
 * @author Burt Beckwith
 */
public abstract class AbstractLauncher {

	protected static final int BUFFER_SIZE = 4096;

	protected static final List<String> SUPPORTED_ARGS = Arrays.asList(
			"context", "host", "port", "httpsPort", "keystorePath", "javax.net.ssl.keyStore",
			"keystorePassword", "javax.net.ssl.keyStorePassword", "truststorePath", "javax.net.ssl.trustStore",
			"trustStorePassword", "javax.net.ssl.trustStorePassword", "enableClientAuth", "workDir",
			"enableCompression", "compressableMimeTypes", "sessionTimeout", "nio", "tomcat.nio",
			"serverName", "enableProxySupport", "enableKillSwitch");

	protected Map<String, String> argsMap;

	protected AbstractLauncher(String[] args) {
		argsMap = argsToMap(args);
	}

	protected File getWorkDir() {
		return new File(getArg("workDir", getArg("java.io.tmpdir", "")));
	}

	protected File extractWar() throws IOException {
		File dir = new File(getWorkDir(), "standalone-war");
		deleteDir(dir);
		dir.mkdirs();
		return extractWar(dir);
	}

	protected File extractWar(File dir) throws IOException {
		return extractWar(getClass().getClassLoader().getResourceAsStream("embedded.war"),
				File.createTempFile("embedded", ".war", dir).getAbsoluteFile());
	}

	protected File extractWar(InputStream embeddedWarfile, File destinationWarfile) throws IOException {
		destinationWarfile.getParentFile().mkdirs();
		destinationWarfile.deleteOnExit();
		copy(embeddedWarfile, new FileOutputStream(destinationWarfile));
		return explode(destinationWarfile);
	}

	protected File explode(File war) throws IOException {
		String basename = war.getName();
		int index = basename.lastIndexOf('.');
		if (index > -1) {
			basename = basename.substring(0, index);
		}
		File explodedDir = new File(war.getParentFile(), basename + "-exploded-" + System.currentTimeMillis());

		ZipFile zipfile = new ZipFile(war);
		for (Enumeration<? extends ZipEntry> e = zipfile.entries(); e.hasMoreElements(); ) {
			unzip(e.nextElement(), zipfile, explodedDir);
		}
		zipfile.close();

		return explodedDir;
	}

	protected void unzip(ZipEntry entry, ZipFile zipfile, File explodedDir) throws IOException {

		if (entry.isDirectory()) {
			new File(explodedDir, entry.getName()).mkdirs();
			return;
		}

		File outputFile = new File(explodedDir, entry.getName());
		if (!outputFile.getParentFile().exists()) {
			outputFile.getParentFile().mkdirs();
		}

		BufferedInputStream inputStream = new BufferedInputStream(zipfile.getInputStream(entry));
		BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));

		try {
			copy(inputStream, outputStream);
		}
		finally {
			outputStream.close();
			inputStream.close();
		}
	}

	protected abstract void start(File exploded) throws IOException, ServletException;

	protected boolean hasLength(String s) {
		return s != null && s.trim().length() > 0;
	}

	protected Map<String, String> argsToMap(String[] args) {
		Map<String, String> map = new HashMap<String, String>();
		for (String arg : args) {
			int index = arg.indexOf('=');
			if (index == -1) {
				System.err.println("Warning, arguments must be specified in name=value format, ignoring argument '" + arg + "'");
				continue;
			}
			String name = arg.substring(0, index).trim();
			if (!SUPPORTED_ARGS.contains(name)) {
				System.err.println("Warning, the specified argument '" + name + "' is not supported, ignoring");
				continue;
			}

			String value = arg.substring(index + 1).trim();
			map.put(name, value);
		}
		return map;
	}

	protected String getArg(String name) {
		return getArg(name, null);
	}

	protected String getArg(String name, String defaultIfMissing) {
		String value = argsMap.get(name);
		if (value == null) {
			if (System.getProperties().containsKey(name)) {
				value = System.getProperty(name);
			}
		}
		if (value == null) {
			value = defaultIfMissing;
		}
		else if (System.getProperties().containsKey(value)) {
			value = System.getProperty(value);
		}
		return value;
	}

	protected int getIntArg(String name, int defaultIfMissing) {
		String value = argsMap.get(name);
		if (value == null) {
			return defaultIfMissing;
		}

		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			return defaultIfMissing;
		}
	}

	protected boolean getBooleanArg(String name, boolean defaultIfMissing) {
		return "true".equalsIgnoreCase(getArg(name, String.valueOf(defaultIfMissing)));
	}

	// from org.springframework.util.FileCopyUtils.copy()
	protected void copy(InputStream in, OutputStream out) throws IOException {
		try {
			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead = -1;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
			out.flush();
		}
		finally {
			try { in.close(); }
			catch (IOException ignored) { /*ignored*/ }
			try { out.close(); }
			catch (IOException ignored) { /*ignored*/ }
		}
	}

	// from DefaultGroovyMethods.deleteDir()
	protected boolean deleteDir(final File dir) {
		if (!dir.exists()) {
			return true;
		}

		if (!dir.isDirectory()) {
			return false;
		}

		File[] files = dir.listFiles();
		if (files == null) {
			return false;
		}

		boolean result = true;
		for (File file : files) {
			if (file.isDirectory()) {
				if (!deleteDir(file)) {
					result = false;
				}
			}
			else {
				if (!file.delete()) {
					result = false;
				}
			}
		}

		if (!dir.delete()) {
			result = false;
		}

		return result;
	}

	protected void deleteExplodedOnShutdown(final File exploded) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				deleteDir(exploded);
			}
		});
	}

	protected Class<?> getKeyToolClass() throws ClassNotFoundException {
		try {
			return Class.forName("sun.security.tools.KeyTool");
		}
		catch (ClassNotFoundException e) {
			// no try/catch for this one, if neither is found let it fail
			return Class.forName("com.ibm.crypto.tools.KeyTool");
		}
	}

	protected void initSsl(File keystoreFile, String keystorePassword, boolean usingUserKeystore) throws IOException {
		if (keystoreFile.exists()) {
			return;
		}

		if (usingUserKeystore) {
			throw new IllegalStateException(
					"cannot start in https because use keystore does not exist (value: " + keystoreFile + ")");
		}

		System.out.println("Creating SSL Certificate...");

		File keystoreDir = keystoreFile.getParentFile();
		if (!keystoreDir.exists() && !keystoreDir.mkdir()) {
			throw new RuntimeException("Unable to create keystore folder: " + keystoreDir.getCanonicalPath());
		}

		try {
			getKeyToolClass().getMethod("main", String[].class).invoke(null, new Object[] { new String[] {
					"-genkey",
					"-alias", "localhost",
					"-dname", "CN=localhost,OU=Test,O=Test,C=US",
					"-keyalg", "RSA",
					"-validity", "365",
					"-storepass", "key",
					"-keystore", keystoreFile.getAbsolutePath(),
					"-storepass", keystorePassword,
					"-keypass", keystorePassword}});
			System.out.println("Created SSL Certificate.");
		}
		catch (Exception e) {
			System.err.println("Unable to create an SSL certificate: " + e.getMessage());
		}
	}
}
