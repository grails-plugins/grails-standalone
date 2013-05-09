/* Copyright 2012-2013 SpringSource
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.TagLibConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;

/**
 * Main class for Jetty; extracts the embedded war and starts Jetty. Inlines
 * some utility methods since the classpath is limited. Based on
 * org.grails.jetty.JettyServer and org.grails.jetty.JettyServerFactory, and
 * also borrows some code from standalone.Start from the jetty-standalone plugin.
 *
 * @author Burt Beckwith
 */
public class JettyLauncher extends AbstractLauncher {

	/**
	 * Start the server.
	 *
	 * @param args optional; 1st is context path, 2nd is host, 3rd is http port,
	 * 4th is SSL port, 5th is SSL keystore path, 6th is keystore password
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		final JettyLauncher launcher = new JettyLauncher();
		final File exploded = launcher.extractWar();
		launcher.deleteExplodedOnShutdown(exploded);
		launcher.start(exploded, args);
	}

	@Override
	protected void start(File exploded, String[] args) throws IOException {

		Map<String, String> argMap = argsToMap(args);

		System.setProperty("org.eclipse.jetty.xml.XmlParser.NotValidating", "true");

		File workDir = new File(System.getProperty("java.io.tmpdir"));
		String contextPath = getArg(argMap, "context", "");
		if (hasLength(contextPath) && !contextPath.startsWith("/")) {
			contextPath = '/' + contextPath;
		}
		String host = getArg(argMap, "host", "localhost");
		int port = getIntArg(argMap, "port", 8080);
		int httpsPort = getIntArg(argMap, "httpsPort", 0);

		String keystorePath = getArg(argMap, "keystorePath", "");
		String keystorePassword = getArg(argMap, "keystorePassword", "");

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

		Server server = configureJetty(contextPath, exploded, host, port, httpsPort, keystoreFile, usingUserKeystore, keystorePassword);

		startJetty(server, host, port, contextPath, httpsPort > 0 ? httpsPort : null);
	}

	protected File extractWebdefaultXml() throws IOException {
		InputStream embeddedWebdefault = getClass().getClassLoader().getResourceAsStream("webdefault.xml");
		File temp = File.createTempFile("webdefault", ".war").getAbsoluteFile();
		temp.getParentFile().mkdirs();
		temp.deleteOnExit();
		copy(embeddedWebdefault, new FileOutputStream(temp));
		return temp;
	}

	protected Server configureJetty(String contextPath, File exploded, String host, int port, int httpsPort,
			File keystoreFile, boolean usingUserKeystore, String keystorePassword) throws IOException {

		WebAppContext context = createStandardContext(exploded.getPath(), contextPath);

		if (httpsPort > 0) {
			return configureHttpsServer(context, port, httpsPort, host, keystoreFile, keystorePassword, usingUserKeystore);
		}

		return configureHttpServer(context, port, host);
	}

	protected void startJetty(Server server, String host, int port, String contextPath, Integer securePort) {
		try {
			server.start();
			String message = "Server running. Browse to http://" +
					(host != null ? host : "localhost") +
					":" + port + contextPath;
			if (securePort != null) {
				message += " or https://" +
						(host != null ? host : "localhost") +
						":" + securePort + contextPath;
			}
			System.out.println(message);
//			System.in.read();
//			server.stop();
//			server.join();
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error loading Jetty: " + e.getMessage());
			System.exit(1);
		}
	}

	protected WebAppContext createStandardContext(String webappRoot, String contextPath) throws IOException {

		// Jetty requires a 'defaults descriptor' on the filesystem
		File webDefaults = extractWebdefaultXml();

		WebAppContext context = new WebAppContext(webappRoot, contextPath);

		setSystemProperty("java.naming.factory.url.pkgs", "org.eclipse.jetty.jndi");
		setSystemProperty("java.naming.factory.initial", "org.eclipse.jetty.jndi.InitialContextFactory");

		Class<?>[] configurationClasses = {
				WebInfConfiguration.class, WebXmlConfiguration.class, MetaInfConfiguration.class,
				FragmentConfiguration.class, EnvConfiguration.class, PlusConfiguration.class,
				JettyWebXmlConfiguration.class, TagLibConfiguration.class };
		Configuration[] configurations = new Configuration[configurationClasses.length];
		for (int i = 0; i < configurationClasses.length; i++) {
			configurations[i] = newConfigurationInstance(configurationClasses[i]);
		}

		context.setConfigurations(configurations);
		context.setDefaultsDescriptor(webDefaults.getPath());

		System.setProperty("TomcatKillSwitch.active", "true"); // workaround to prevent server exiting

		return context;
	}

	protected Configuration newConfigurationInstance(Class<?> clazz) {
		 try {
			return (Configuration)clazz.newInstance();
		}
		catch (InstantiationException e) {
			throw new RuntimeException(e);
		}
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	protected Server configureHttpServer(WebAppContext context, int serverPort, String serverHost) {

		Server server = new Server(serverPort);
		Connector connector = server.getConnectors()[0];

		// Set some timeout options to make debugging easier.
		connector.setMaxIdleTime(1000 * 60 * 60);
		if (connector instanceof SocketConnector) {
			((SocketConnector)connector).setSoLingerTime(-1);
		}

		if (hasLength(serverHost)) {
			connector.setHost(serverHost);
		}

		server.setHandler(context);
		return server;
	}

	protected Server configureHttpsServer(WebAppContext context, int httpPort, int httpsPort, String serverHost, File keystoreFile,
			String keystorePassword, boolean usingUserKeystore) throws IOException {

		Server server = configureHttpServer(context, httpPort, serverHost);

		initSsl(keystoreFile, keystorePassword, usingUserKeystore);
		createSslConnector(server, httpsPort, serverHost, keystoreFile, keystorePassword);

		return server;
	}

	protected void createSslConnector(Server server, int httpsPort, String serverHost, File keystoreFile, String keystorePassword) {

		SslSocketConnector secureListener = new SslSocketConnector();
		secureListener.setPort(httpsPort);
		if (hasLength(serverHost)) {
			secureListener.setHost(serverHost);
		}
		secureListener.setMaxIdleTime(50000);
		secureListener.setPassword(keystorePassword);
		secureListener.setKeyPassword(keystorePassword);
		secureListener.setKeystore(keystoreFile.getAbsolutePath());
		secureListener.setNeedClientAuth(false);
		secureListener.setWantClientAuth(true);

		Connector[] connectors = server.getConnectors();
		Connector[] allConnectors = new Connector[connectors.length + 1];
		System.arraycopy(connectors, 0, allConnectors, 0, connectors.length);
		allConnectors[connectors.length] = secureListener;
		server.setConnectors(allConnectors);
	}

	protected void setSystemProperty(String name, String value) {
		if (!hasLength(System.getProperty(name))) {
			System.setProperty(name, value);
		}
	}
}
