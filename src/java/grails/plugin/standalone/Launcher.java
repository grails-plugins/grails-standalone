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
package grails.plugin.standalone;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.Http11NioProtocol;

/**
 * Main class; extracts the embedded war and starts Tomcat.
 *
 * @author Burt Beckwith
 */
public class Launcher {

	protected static final int BUFFER_SIZE = 4096;

	/**
	 * Start the server.
	 * @param args args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		Launcher launcher = new Launcher();
		File war = launcher.extractWar();
		launcher.start(war, args);
	}

	protected File extractWar() throws FileNotFoundException, IOException {
		InputStream embeddedWarfile = Launcher.class.getClassLoader().getResourceAsStream("embedded.war");
		File tempWarfile = File.createTempFile("embedded", ".war").getAbsoluteFile();
		tempWarfile.getParentFile().mkdirs();
		tempWarfile.deleteOnExit();

		String embeddedWebroot = "";
		File tempWebroot = new File(tempWarfile.getParentFile(), embeddedWebroot);
		tempWebroot.mkdirs();

		copy(embeddedWarfile, new FileOutputStream(tempWarfile));
		return tempWarfile;
	}

	protected void copy(InputStream in, FileOutputStream out) throws IOException {
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
			catch (IOException ignored) {
				// ignored
			}
			try { out.close(); }
			catch (IOException ignored) {
				// ignored
			}
		}
	}

	protected void start(File war, String[] args) throws IOException {
		File workDir = new File(System.getProperty("java.io.tmpdir"));
		File tomcatDir = new File(workDir, "tomcat");
		boolean usingUserKeystore;
		File keystoreFile;
		String keyPassword = "";
		String contextPath = "";
		boolean useNio = false;

		String userKeystore = null;
		if (hasLength(userKeystore)) {
			usingUserKeystore = true;
			keystoreFile = new File(userKeystore);
//			keyPassword = getConfigParam("keystorePassword") ?: "changeit" // changeit is the keystore default
		}
		else {
			usingUserKeystore = false;
			keystoreFile = new File(workDir, "ssl/keystore");
			keyPassword = "123456";
		}

//		tomcatDir.deleteDir();

		String host = "localhost";
		int port = 8080;
		int httpsPort = 0; //8443;

		String keystorePath = "";
		String keystorePassword = "";
		if (httpsPort > 0) {
			keystorePath = "";
			keystorePassword = "";
		}

		final Tomcat tomcat = new Tomcat();
		tomcat.setPort(port);

		if (useNio) {
			System.out.println("Enabling Tomcat NIO Connector");
			Connector connector = new Connector(Http11NioProtocol.class.getName());
			connector.setPort(port);
			tomcat.getService().addConnector(connector);
			tomcat.setConnector(connector);
		}

		tomcat.setBaseDir(tomcatDir.getPath());
		try {
			tomcat.addWebapp(contextPath, war.getAbsolutePath());
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error loading Tomcat: " + e.getMessage());
			System.exit(1);
		}
		tomcat.enableNaming();

		final Connector connector = tomcat.getConnector();

		// Only bind to host name if we aren't using the default
		if (!host.equals("localhost")) {
			connector.setAttribute("address", host);
		}

		connector.setURIEncoding("UTF-8");

		if (httpsPort > 0) {
			initSsl(keystoreFile, keyPassword, usingUserKeystore);

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
			sslConnector.setAttribute("keystoreFile", keystorePath);
			sslConnector.setAttribute("keystorePass", keystorePassword);
			sslConnector.setURIEncoding("UTF-8");

			if (!host.equals("localhost")) {
				sslConnector.setAttribute("address", host);
			}

			tomcat.getService().addConnector(sslConnector);
		}

		final int serverPort = port;
		new Thread(new Runnable() {
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
		}).start();

		try {
			tomcat.start();
			String message = "Server running. Browse to http://"+(host != null ? host : "localhost")+":"+port+contextPath;
			System.out.println(message);
		}
		catch (LifecycleException e) {
			e.printStackTrace();
			System.err.println("Error loading Tomcat: " + e.getMessage());
			System.exit(1);
		}
	}

	protected ServerSocket createKillSwitch(int killListenerPort) {
		try {
			return new ServerSocket(killListenerPort);
		}
		catch (IOException e) {
			return null;
		}
	}

	protected void initSsl(File keystoreFile, String keyPassword, boolean usingUserKeystore) throws IOException {
		if (!keystoreFile.exists()) {
			if (usingUserKeystore) {
				throw new IllegalStateException(
						"cannot start tomcat in https because use keystore does not exist (value: " + keystoreFile + ")");
			}
			createSSLCertificate(keystoreFile, keyPassword);
		}
	}

	protected void createSSLCertificate(File keystoreFile, String keyPassword) throws IOException {
		System.out.println("Creating SSL Certificate...");

		File keystoreDir = keystoreFile.getParentFile();
		if (!keystoreDir.exists() && !keystoreDir.mkdir()) {
			throw new RuntimeException("Unable to create keystore folder: " + keystoreDir.getCanonicalPath());
		}

//		getKeyToolClass().main(
//				"-genkey",
//				"-alias", "localhost",
//				"-dname", "CN=localhost,OU=Test,O=Test,C=US",
//				"-keyalg", "RSA",
//				"-validity", "365",
//				"-storepass", "key",
//				"-keystore", keystoreFile.getAbsolutePath(),
//				"-storepass", keyPassword,
//				"-keypass", keyPassword);

		System.out.println("Created SSL Certificate.");
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
}
