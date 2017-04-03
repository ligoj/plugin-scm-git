package org.ligoj.app.plugin.scm.git;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.dao.ParameterValueRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.scm.git.GitPluginResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.sf.ehcache.CacheManager;

/**
 * Test class of {@link GitPluginResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class GitPluginResourceTest extends AbstractServerTest {
	@Autowired
	private GitPluginResource resource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private ParameterValueRepository parameterValueRepository;

	protected int subscription;

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("gStack");
		CacheManager.getInstance().getCache("node-parameters").removeAll();
		CacheManager.getInstance().getCache("subscription-parameters").removeAll();
		CacheManager.getInstance().getCache("nodes").removeAll();

		// Coverage only
		resource.getKey();
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is
	 * only one subscription for a service.
	 */
	protected Integer getSubscription(final String project) {
		return getSubscription(project, GitPluginResource.KEY);
	}

	@Test
	public void delete() throws Exception {
		resource.delete(subscription, false);
		em.flush();
		em.clear();
		// No custom data -> nothing to check;
	}

	@Test
	public void getVersion() throws Exception {
		Assert.assertNull(resource.getVersion(subscription));
	}

	@Test
	public void getLastVersion() throws Exception {
		Assert.assertNull(resource.getLastVersion());
	}

	@Test
	public void link() throws Exception {
		prepareMockRepository();
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(this.subscription);

		// Nothing to validate for now...
	}

	@Test
	public void linkNotFound() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("service:scm:git:repository", "git-repository"));

		prepareMockRepository();
		httpServer.start();

		parameterValueRepository.findAllBySubscription(subscription).stream()
				.filter(v -> v.getParameter().getId().equals(GitPluginResource.KEY + ":repository")).findFirst().get()
				.setData("0");
		em.flush();
		em.clear();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(this.subscription);

		// Nothing to validate for now...
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		prepareMockRepository();
		Assert.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription))
				.getStatus().isUp());
	}

	@Test
	public void checkSubscriptionStatusAnonymous() throws Exception {
		prepareMockRepository();

		// Remove user from the parameters to be anonymous
		final Map<String, String> parametersNoCheck = subscriptionResource.getParametersNoCheck(subscription);
		parametersNoCheck.remove("service:scm:git:user");
		Assert.assertTrue(resource.checkSubscriptionStatus(parametersNoCheck).getStatus().isUp());
	}

	private void prepareMockRepository() throws IOException {
		// --> /gfi-gstack/info/refs?service=git-upload-pack
		httpServer.stubFor(
				get(urlPathEqualTo("/gfi-gstack/info/refs")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withHeader("Content-Type", "application/x-git-upload-pack-advertisement")
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/scm/git/git-upload-pack").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockAdmin() throws IOException {
		httpServer.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(new ClassPathResource("mock-server/scm/index.html").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

	@Test
	public void checkStatus() throws Exception {
		prepareMockAdmin();
		Assert.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void checkStatusAuthenticationFailed() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(GitPluginResource.KEY + ":url", "git-admin"));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
	}

	@Test
	public void checkStatusNotAdmin() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(GitPluginResource.KEY + ":url", "git-admin"));
		httpServer.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
	}

	@Test
	public void checkStatusInvalidIndex() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(GitPluginResource.KEY + ":url", "git-admin"));
		httpServer.stubFor(get(urlPathEqualTo("/"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html>some</html>")));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
	}

	@Test
	public void checkStatusGitProtocol() throws Exception {
		em.createQuery("UPDATE ParameterValue SET data=:data WHERE parameter.id=:parameter")
				.setParameter("data", "git://any").setParameter("parameter", "service:scm:git:url").executeUpdate();
		Assert.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void checkStatusNoIndex() throws Exception {
		em.createQuery("UPDATE ParameterValue SET data=:data WHERE parameter.id=:parameter")
				.setParameter("data", Boolean.FALSE.toString()).setParameter("parameter", "service:scm:git:index")
				.executeUpdate();
		Assert.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void findAllByName() throws Exception {
		prepareMockAdmin();
		httpServer.start();

		final List<NamedBean<String>> projects = resource.findAllByName("service:scm:git:dig", "as-");
		Assert.assertEquals(4, projects.size());
		Assert.assertEquals("has-evamed", projects.get(0).getId());
		Assert.assertEquals("has-evamed", projects.get(0).getName());
	}

}
