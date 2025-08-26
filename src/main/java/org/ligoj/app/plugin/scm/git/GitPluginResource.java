package org.ligoj.app.plugin.scm.git;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.eclipse.jgit.util.HttpSupport;
import org.ligoj.app.plugin.scm.AbstractIndexBasedPluginResource;
import org.ligoj.app.plugin.scm.ScmResource;
import org.ligoj.app.plugin.scm.ScmServicePlugin;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * GIT resource. jGit is used to perform validations.
 */
@Path(GitPluginResource.URL)
@Component
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class GitPluginResource extends AbstractIndexBasedPluginResource implements ScmServicePlugin {

	/**
	 * SSL verification configuration name.
	 */
	private static final String CONF_SSL_VERIFY = "service:scm:git:sslVerify";

	/**
	 * Plug-in key.
	 */
	public static final String URL = ScmResource.SERVICE_URL + "/git";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	@Autowired
	private ConfigurationResource configuration;

	/**
	 * Constructor specifying a Git implementation.
	 */
	public GitPluginResource() {
		super(KEY, "git");
	}

	@Override
	public String validateRepository(final Map<String, String> parameters) {
		// Use jGit {@link LsRemoteCommand} to validate remote GIT repository
		final String url = super.getRepositoryUrl(parameters);

		final LsRemoteCommand command = new LsRemoteCommand(null).setRemote(url);
		final String username = parameters.get(parameterUser);
		if (StringUtils.isNotBlank(username)) {
			// Authentication is required
			command.setCredentialsProvider(
					new UsernamePasswordCredentialsProvider(username, parameters.get(parameterPassword)));
		}
		// Get the repository information
		try {
			return command.call().toString();
		} catch (final GitAPIException e) {
			// Invalid Git repository
			log.error("Git validation failed for url {}", url, e);
			throw new ValidationJsonException(parameterRepository, simpleName + "-repository",
					parameters.get(parameterRepository));
		}
	}

	/**
	 * Configure the SSL factory.
	 */
	@PostConstruct
	public void configureConnectionFactory() {
		// Ignore SSL verification
		HttpTransport.setConnectionFactory(new InsecureHttpConnectionFactory());
	}

	/**
	 * Custom connection factory disabling SSL verification.
	 * 
	 * @see <a href= "https://stackoverflow.com/questions/33998477">stackoverflow</a>
	 */
	protected class InsecureHttpConnectionFactory extends JDKHttpConnectionFactory {

		@Override
		public HttpConnection create(URL url) throws IOException {
			return create(url, super.create(url));
		}

		@Override
		public HttpConnection create(URL url, Proxy proxy) throws IOException {
			final HttpConnection connection = super.create(url, proxy);
			return create(url, connection);
		}

		private HttpConnection create(URL url, final HttpConnection connection) throws IOException {
			if (!"http".equals(url.getProtocol())
					&& !BooleanUtils.toBoolean(ObjectUtils.getIfNull(configuration.get(CONF_SSL_VERIFY), "true"))) {
				// Disable SSL verification only for HTTPS
				HttpSupport.disableSslVerify(connection);
			}
			return connection;
		}
	}
}
