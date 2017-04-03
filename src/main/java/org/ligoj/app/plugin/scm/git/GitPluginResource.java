package org.ligoj.app.plugin.scm.git;

import java.util.Map;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.ligoj.app.plugin.scm.AbstractIndexBasedPluginResource;
import org.ligoj.app.plugin.scm.ScmResource;
import org.ligoj.app.plugin.scm.ScmServicePlugin;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.stereotype.Component;

/**
 * GIT resource. jGit is used to perform validations.
 */
@Path(GitPluginResource.URL)
@Component
@Produces(MediaType.APPLICATION_JSON)
public class GitPluginResource extends AbstractIndexBasedPluginResource implements ScmServicePlugin {

	/**
	 * Plug-in key.
	 */
	public static final String URL = ScmResource.SERVICE_URL + "/git";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	/**
	 * Constructor specifying a Git implementation.
	 */
	public GitPluginResource() {
		super(KEY, "git");
	}

	@Override
	public String validateRepository(final Map<String, String> parameters) {
		// Use jGit {@link LsRemoteCommand} to validate remote GIT repository
		final LsRemoteCommand command = new LsRemoteCommand(null).setRemote(super.getRepositoryUrl(parameters));
		final String username = parameters.get(parameterUser);
		if (StringUtils.isNotBlank(username)) {
			// Authentication is required
			command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, parameters.get(parameterPassword)));
		}
		try {
			return command.call().toString();
		} catch (final GitAPIException e) {
			// Invalid Git repository
			throw new ValidationJsonException(parameterRepository, simpleName + "-repository", parameters.get(parameterRepository), e);
		}
	}
}
