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
package grails.plugin.standalone;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.CrawlerSessionManagerValve;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.coyote.http2.Http2Protocol;

/**
 * Main class; extracts the embedded war and starts Tomcat. Inlines some utility
 * methods since the classpath is limited. Based on
 * org.grails.plugins.tomcat.IsolatedTomcat and
 * org.grails.plugins.tomcat.IsolatedWarTomcatServer.
 *
 * Also borrowed some code from https://github.com/jsimone/webapp-runner
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
public class Launcher extends AbstractLauncher {

	protected Tomcat tomcat = new Tomcat();
	protected Context context;

	/**
	 * Start the server.
	 *
	 * @param args
	 *           optional, these are supported<br/>
	 *           <ul>
	 *           <li>workDir, defaults to 'java.io.tmpdir' system property</li>
	 *           <li>context, defaults to ''</li>
	 *           <li>host, defaults to 'localhost'</li>
	 *           <li>port, defaults to 8080</li>
	 *           <li>httpsPort, no default</li>
	 *           <li>keystorePath or javax.net.ssl.keyStore, no default</li>
	 *           <li>keystorePassword or javax.net.ssl.keyStorePassword, no default</li>
	 *           <li>truststorePath or javax.net.ssl.trustStore, no default</li>
	 *           <li>trustStorePassword or javax.net.ssl.trustStorePassword, no default</li>
	 *           <li>enableCompression, defaults to true</li>
	 *           <li>compressableMimeTypes, defaults to ''</li>
	 *           <li>enableClientAuth, defaults to 'want'</li>
	 *           <li>sessionTimeout, defaults to 30 (minutes)</li>
	 *           <li>nio or tomcat.nio, defaults to true</li>
	 *           <li>serverName, a specific value to use as HTTP Server Header, no default</li>
	 *           <li>enableProxySupport, enables support for X-Forwarded headers, defaults to false</li>
	 *           <li>certificateFile, the path to the OpenSSL certificate file, no default</li>
	 *           <li>certificateKeyFile, the path to the OpenSSL certificate private key file, no default</li>
	 *           <li>certificateKeyPassword, the password for the OpenSSL certificate private key file, no default</li>
	 *           </ul>
	 *           In addition, if you specify a value that is the name of a system
	 *           property (e.g. 'home.dir'), the system property value will be used.
	 */
	public static void main(String[] args) {
		try {
			final Launcher launcher = new Launcher(args);
			final File exploded = launcher.extractWar();
			launcher.deleteExplodedOnShutdown(exploded);
			launcher.start(exploded);
		}
		catch (Exception e) {
			die(e, "Error loading Tomcat: " + e.getMessage());
		}
	}

	public Launcher(String[] args) {
		super(args);
	}

	@Override
	protected void start(File exploded) throws IOException, ServletException {

		File workDir = getWorkDir();
		String contextPath = getArg("context", "");
		if (hasLength(contextPath) && !contextPath.startsWith("/")) {
			contextPath = '/' + contextPath;
		}
		String host = getArg("host", "localhost");
		int port = getIntArg("port", 8080);
		int httpsPort = getIntArg("httpsPort", 0);

		String certificateFile = getArg("certificateFile", "");
		String certificateKeyFile = getArg("certificateKeyFile", "");
		String certificateKeyPassword = getArg("certificateKeyPassword", "");
		String keystorePath = getArg("keystorePath", getArg("javax.net.ssl.keyStore", ""));
		String keystorePassword = getArg("keystorePassword", getArg("javax.net.ssl.keyStorePassword", ""));
		String truststorePath = getArg("truststorePath", getArg("javax.net.ssl.trustStore", ""));
		String trustStorePassword = getArg("trustStorePassword", getArg("javax.net.ssl.trustStorePassword", ""));

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

		boolean enableCompression = getBooleanArg("enableCompression", true);
		String compressableMimeTypes = getArg("compressableMimeTypes", "");
		String enableClientAuth = getArg("enableClientAuth", "want");
		int sessionTimeout = getIntArg("sessionTimeout", 30);
		String nio = getArg("nio", getArg("tomcat.nio"));
		boolean useNio = nio == null || nio.equalsIgnoreCase("true");
		String serverName = getArg("serverName", null);
		boolean enableProxySupport = getBooleanArg("enableProxySupport", false);

		configureTomcat(tomcatDir, contextPath, exploded, host, port,
				httpsPort, keystoreFile, keystorePassword, usingUserKeystore,
				enableClientAuth, truststorePath, trustStorePassword,
				sessionTimeout, enableCompression, compressableMimeTypes, useNio,
				serverName, enableProxySupport, certificateFile,
				certificateKeyFile, certificateKeyPassword);

		startKillSwitchThread(port);
		addShutdownHook();
		addFailureLifecycleListener(contextPath);

		startTomcat(host, port, contextPath, httpsPort > 0 ? httpsPort : null);
	}

	protected void configureTomcat(File tomcatDir, String contextPath, File exploded,
			String host, int port, int httpsPort, File keystoreFile, String keystorePassword,
			boolean usingUserKeystore, String enableClientAuth,
			String truststorePath, String trustStorePassword, Integer sessionTimeout,
			boolean enableCompression, String compressableMimeTypes, boolean useNio,
			String serverName, boolean enableProxySupport, String certificateFile,
			String certificateKeyFile, String certificateKeyPassword) throws IOException, ServletException {

		tomcat.setPort(port);

		tomcat.setBaseDir(tomcatDir.getPath());
		context = tomcat.addWebapp(contextPath, exploded.getAbsolutePath());

		WebResourceRoot resources = new StandardRoot(context);
		resources.setCacheMaxSize(50 * 1024 * 1024);
		context.setResources(resources);

		tomcat.enableNaming();

		if (useNio) {
			addNioConnector(port);
		}

		tomcat.getEngine().getPipeline().addValve(new CrawlerSessionManagerValve());

		if (enableProxySupport) {
			RemoteIpValve remoteIpValve = new RemoteIpValve();
			remoteIpValve.setProtocolHeader("X-Forwarded-Proto");
			tomcat.getEngine().getPipeline().addValve(remoteIpValve);
		}

		Connector connector = tomcat.getConnector();

		if (serverName != null) {
			connector.setProperty("server", serverName);
		}

		if (enableCompression) {
			connector.setProperty("compression", "on");
			connector.setProperty("compressableMimeType", compressableMimeTypes);
		}

		// Only bind to host name if we aren't using the default
		if (!host.equals("localhost")) {
			connector.setAttribute("address", host);
		}

		connector.setURIEncoding("UTF-8");

		context.setSessionTimeout(sessionTimeout);

		if (httpsPort > 0) {
			initSsl(keystoreFile, keystorePassword, usingUserKeystore);
			createSslConnector(httpsPort, keystoreFile, keystorePassword, truststorePath, trustStorePassword, host,
					enableClientAuth, certificateFile, certificateKeyFile, certificateKeyPassword);
		}
	}

	protected void startKillSwitchThread(final int serverPort) {
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
							die(e, "Error stopping Tomcat: " + e.getMessage());
						}
					}
					catch (IOException e) {
						// just exit
					}
				}
			}
		}.start();
	}

	protected void startTomcat(String host, int port, String contextPath, Integer securePort) {
		try {
			tomcat.start();
			logStartMessage(host, port, securePort, contextPath);
		}
		catch (LifecycleException e) {
			die(e, "Error loading Tomcat: " + e.getMessage());
		}
	}

	protected void createSslConnector(int httpsPort, File keystoreFile, String keystorePassword,
			String truststorePath, String trustStorePassword, String host, String enableClientAuth,
			String certificateFile, String certificateKeyFile, String certificateKeyPassword) {

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
		sslConnector.setURIEncoding("UTF-8");

		if (hasLength(certificateKeyFile) && hasLength(certificateFile)) {
			sslConnector.setAttribute("SSLHonorCipherOrder", false);
			sslConnector.setAttribute("SSLCertificateKeyFile", certificateKeyFile);
			sslConnector.setAttribute("SSLCertificateFile", certificateFile);
			if (hasLength(certificateKeyPassword)) {
				sslConnector.setAttribute("SSLPassword", certificateKeyPassword);
			}
		}
		else {
			sslConnector.setAttribute("keystoreFile", keystoreFile.getAbsolutePath());
			sslConnector.setAttribute("keystorePass", keystorePassword);

			if (hasLength(truststorePath)) {
				sslConnector.setProperty("sslProtocol", "tls");
				sslConnector.setAttribute("truststoreFile", new File(truststorePath).getAbsolutePath());
				sslConnector.setAttribute("trustStorePassword", trustStorePassword);
			}
		}

		sslConnector.setAttribute("clientAuth", enableClientAuth);

		if (!host.equals("localhost")) {
			sslConnector.setAttribute("address", host);
		}

		sslConnector.addUpgradeProtocol(new Http2Protocol());

		AprLifecycleListener aprLifecycleListener = new AprLifecycleListener();
		aprLifecycleListener.setSSLEngine("on");
		aprLifecycleListener.setUseAprConnector(true);
		tomcat.getServer().addLifecycleListener(aprLifecycleListener);

		tomcat.getService().addConnector(sslConnector);
	}

	protected void addNioConnector(int port) {
		System.out.println("Enabling Tomcat NIO Connector");
		Connector connector = new Connector(Http11NioProtocol.class.getName());
		connector.setPort(port);
		tomcat.setConnector(connector);
		tomcat.getService().addConnector(tomcat.getConnector());
	}

	protected ServerSocket createKillSwitch(int killListenerPort) {
		try {
			return new ServerSocket(killListenerPort);
		}
		catch (IOException e) {
			return null;
		}
	}

	/**
	 * Stops the embedded Tomcat server.
	 */
	protected void addShutdownHook() {
		// add shutdown hook to stop server
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					if (tomcat != null) {
						tomcat.getServer().stop();
					}
				}
				catch (LifecycleException e) {
					throw new RuntimeException("WARNING: Cannot Stop Tomcat " + e.getMessage(), e);
				}
			}
		});
	}

	protected void addFailureLifecycleListener(final String contextName) {
		// allow Tomcat to shutdown if a context failure is detected
		context.addLifecycleListener(new LifecycleListener() {
			public void lifecycleEvent(LifecycleEvent event) {
				if (event.getLifecycle().getState() == LifecycleState.FAILED) {
					Server server = tomcat.getServer();
					if (server instanceof StandardServer) {
						System.err.println("SEVERE: Context [" + contextName + "] failed in [" +
								event.getLifecycle().getClass().getName() + "] lifecycle. Allowing Tomcat to shutdown.");
						((StandardServer) server).stopAwait();
					}
				}
			}
		});
	}
}
