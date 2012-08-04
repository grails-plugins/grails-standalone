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
package grails.plugin.standalone;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.CrawlerSessionManagerValve;
import org.apache.coyote.http11.Http11NioProtocol;

/**
 * Main class; extracts the embedded war and starts Tomcat. Inlines some utility methods since
 * the classpath is limited. Based on org.grails.plugins.tomcat.IsolatedTomcat and
 * org.grails.plugins.tomcat.IsolatedWarTomcatServer.
 *
 * @author Burt Beckwith
 */
public class Launcher {

	protected static final int BUFFER_SIZE = 4096;

	/**
	 * Start the server.
	 *
	 * @param args optional; 1st is context path, 2nd is host, 3rd is http port,
	 * 4th is SSL port, 5th is SSL keystore path, 6th is keystore password
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		final Launcher launcher = new Launcher();
		final File exploded = launcher.extractWar();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				launcher.deleteDir(exploded);
			}
		});
		launcher.start(exploded, args);
	}

	protected File extractWar() throws IOException {
		InputStream embeddedWarfile = Launcher.class.getClassLoader().getResourceAsStream("embedded.war");
		File tempWarfile = File.createTempFile("embedded", ".war").getAbsoluteFile();
		tempWarfile.getParentFile().mkdirs();
		tempWarfile.deleteOnExit();
		copy(embeddedWarfile, new FileOutputStream(tempWarfile));
		return explode(tempWarfile);
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

	protected void start(File exploded, String[] args) throws IOException {

		File workDir = new File(System.getProperty("java.io.tmpdir"));
		String contextPath = "";
		if (args.length > 0) {
			contextPath = args[0];
		}
		if (hasLength(contextPath) && !contextPath.startsWith("/")) {
			contextPath = '/' + contextPath;
		}
		String host = "localhost";
		if (args.length > 1) {
			host = args[1];
		}
		int port = argToNumber(args, 2, 8080);
		int httpsPort = argToNumber(args, 3, 0);

		String keystorePath = "";
		String keystorePassword = "";
		if (httpsPort > 0 && args.length > 5) {
			keystorePath = args[4];
			keystorePassword = args[5];
		}

		boolean usingUserKeystore;
		File keystoreFile;
		if (hasLength(keystorePath)) {
			usingUserKeystore = true;
			keystoreFile = new File(keystorePath);
		}
		else {
			usingUserKeystore = false;
			keystoreFile = new File(workDir, "ssl/keystore");
			keystorePassword = "123456";
		}

		File tomcatDir = new File(workDir, "grails-standalone-tomcat");
		deleteDir(tomcatDir);

		Tomcat tomcat = configureTomcat(tomcatDir, contextPath, exploded, host, port,
				httpsPort, keystoreFile, keystorePassword, usingUserKeystore);

		startKillSwitchThread(tomcat, port);

		startTomcat(tomcat, host, port, contextPath, httpsPort > 0 ? httpsPort : null);
	}

	protected Tomcat configureTomcat(File tomcatDir, String contextPath, File exploded,
			String host, int port, int httpsPort, File keystoreFile,
			String keystorePassword, boolean usingUserKeystore) throws IOException {

		Tomcat tomcat = new Tomcat();
		tomcat.setPort(port);

		tomcat.setBaseDir(tomcatDir.getPath());
		try {
			tomcat.addWebapp(contextPath, exploded.getAbsolutePath());
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error loading Tomcat: " + e.getMessage());
			System.exit(1);
		}
		tomcat.enableNaming();

		addNioConnector(tomcat, port);

		tomcat.getEngine().getPipeline().addValve(new CrawlerSessionManagerValve());

		Connector connector = tomcat.getConnector();

		// Only bind to host name if we aren't using the default
		if (!host.equals("localhost")) {
			connector.setAttribute("address", host);
		}

		connector.setURIEncoding("UTF-8");

		if (httpsPort > 0) {
			initSsl(keystoreFile, keystorePassword, usingUserKeystore);
			createSslConnector(tomcat, httpsPort, keystoreFile, keystorePassword, host);
		}

		return tomcat;
	}

	protected void startKillSwitchThread(final Tomcat tomcat, final int serverPort) {
		new Thread() {
			@Override
			public void run() {
				int killListenerPort = serverPort + 1;
				ServerSocket serverSocket = createKillSwitch(killListenerPort);
				if (serverSocket != null) {
					try {
						serverSocket.accept();
						try {
							tomcat.stop();
						}
						catch (LifecycleException e) {
							System.err.println("Error stopping Tomcat: " + e.getMessage());
							System.exit(1);
						}
					}
					catch (IOException e) {
						// just exit
					}
				}
			}
		}.start();
	}

	protected void startTomcat(Tomcat tomcat, String host, int port, String contextPath, Integer securePort) {
		try {
			tomcat.start();
			String message = "Server running. Browse to http://" +
					(host != null ? host : "localhost") +
					":" + port + contextPath;
			if (securePort != null) {
				message += " or https://" +
						(host != null ? host : "localhost") +
						":" + securePort + contextPath;
			}
			System.out.println(message);
		}
		catch (LifecycleException e) {
			e.printStackTrace();
			System.err.println("Error loading Tomcat: " + e.getMessage());
			System.exit(1);
		}
	}

	protected void createSslConnector(Tomcat tomcat, int httpsPort, File keystoreFile,
			String keystorePassword, String host) {

		Connector sslConnector;
		try {
			sslConnector = new Connector();
		}
		catch (Exception e) {
			throw new RuntimeException("Couldn't create HTTPS connector", e);
		}

		sslConnector.setScheme("https");
		sslConnector.setSecure(true);
		sslConnector.setPort(httpsPort);
		sslConnector.setProperty("SSLEnabled", "true");
		sslConnector.setAttribute("keystoreFile", keystoreFile.getAbsolutePath());
		sslConnector.setAttribute("keystorePass", keystorePassword);
		sslConnector.setURIEncoding("UTF-8");

		if (!host.equals("localhost")) {
			sslConnector.setAttribute("address", host);
		}

		tomcat.getService().addConnector(sslConnector);
	}

	protected void addNioConnector(Tomcat tomcat, int port) {
		boolean useNio = Boolean.getBoolean("tomcat.nio");
		if (!useNio) {
			return;
		}

		System.out.println("Enabling Tomcat NIO Connector");
		Connector connector = new Connector(Http11NioProtocol.class.getName());
		connector.setPort(port);
		tomcat.getService().addConnector(connector);
		tomcat.setConnector(connector);
	}

	protected ServerSocket createKillSwitch(int killListenerPort) {
		try {
			return new ServerSocket(killListenerPort);
		}
		catch (IOException e) {
			return null;
		}
	}

	protected void initSsl(File keystoreFile, String keystorePassword, boolean usingUserKeystore) throws IOException {
		if (keystoreFile.exists()) {
			return;
		}

		if (usingUserKeystore) {
			throw new IllegalStateException(
					"cannot start tomcat in https because use keystore does not exist (value: " + keystoreFile + ")");
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

	protected Class<?> getKeyToolClass() throws ClassNotFoundException {
		try {
			return Class.forName("sun.security.tools.KeyTool");
		}
		catch (ClassNotFoundException e) {
			// no try/catch for this one, if neither is found let it fail
			return Class.forName("com.ibm.crypto.tools.KeyTool");
		}
	}

	protected boolean hasLength(String s) {
		return s != null && s.trim().length() > 0;
	}

	protected int argToNumber(String[] args, int i, int orDefault) {
		if (args.length > i) {
			try {
				return Integer.parseInt(args[i]);
			}
			catch (NumberFormatException e) {
				return orDefault;
			}
		}
		return orDefault;
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
}
