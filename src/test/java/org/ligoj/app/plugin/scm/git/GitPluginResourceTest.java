package org.ligoj.app.plugin.scm.git;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.dao.ParameterValueRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.sf.ehcache.CacheManager;

/**
 * Test class of {@link GitPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class GitPluginResourceTest extends AbstractServerTest {
	@Autowired
	private GitPluginResource resource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private ConfigurationResource configuration;

	@Autowired
	private ParameterValueRepository parameterValueRepository;

	protected int subscription;

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("gStack");
		CacheManager.getInstance().getCache("node-parameters").removeAll();
		CacheManager.getInstance().getCache("subscription-parameters").removeAll();
		CacheManager.getInstance().getCache("nodes").removeAll();
		configuration.delete("service:scm:git:sslVerify");

		// Coverage only
		resource.getKey();
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is only one subscription for a service.
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
		Assertions.assertNull(resource.getVersion(subscription));
	}

	@Test
	public void getLastVersion() throws Exception {
		Assertions.assertNull(resource.getLastVersion());
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
		prepareMockRepository();
		httpServer.start();

		parameterValueRepository.findAllBySubscription(subscription).stream()
				.filter(v -> v.getParameter().getId().equals(GitPluginResource.KEY + ":repository")).findFirst().get()
				.setData("0");
		em.flush();
		em.clear();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.link(this.subscription);
		}), "service:scm:git:repository", "git-repository");
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		prepareMockRepository();
		Assertions.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription))
				.getStatus().isUp());
	}

	@Test
	public void checkSubscriptionStatusAnonymous() throws Exception {
		prepareMockRepository();

		// Remove user from the parameters to be anonymous
		final Map<String, String> parametersNoCheck = subscriptionResource.getParametersNoCheck(subscription);
		parametersNoCheck.remove("service:scm:git:user");
		Assertions.assertTrue(resource.checkSubscriptionStatus(parametersNoCheck).getStatus().isUp());
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
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void checkSubscriptionStatusSsl() throws Exception {
		final Map<String, String> parametersNoCheck = mockHtps();
		configuration.saveOrUpdate("service:scm:git:sslVerify", "false");
		Assertions.assertTrue(resource.checkSubscriptionStatus(parametersNoCheck).getStatus().isUp());
		resource.new InsecureHttpConnectionFactory().create(new URL("https", "github.com", "ligoj/ligoj.git"));
	}

	@Test
	public void checkSubscriptionStatusSslVerifyOff() throws Exception {
		final Map<String, String> parametersNoCheck = mockHtps();
		configuration.saveOrUpdate("service:scm:git:sslVerify", "true");
		Assertions.assertTrue(resource.checkSubscriptionStatus(parametersNoCheck).getStatus().isUp());
	}

	private Map<String, String> mockHtps() throws IOException {
		// This does not work because of Wiremock Jetty 9.2/9.4
		// httpServer = new WireMockServer(MOCK_PORT, MOCK_PORT + 2);
		prepareMockRepository();

		// Remove user from the parameters to be anonymous
		final Map<String, String> parametersNoCheck = subscriptionResource.getParametersNoCheck(subscription);
		parametersNoCheck.remove("service:scm:git:user");
		parametersNoCheck.put("service:scm:git:url", "https://github.com/ligoj");
		parametersNoCheck.put("service:scm:git:repository", "ligoj.git");
		return parametersNoCheck;
	}

	@Test
	public void checkStatusAuthenticationFailed() {
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), GitPluginResource.KEY + ":url", "git-admin");
	}

	@Test
	public void checkStatusNotAdmin() {
		httpServer.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), GitPluginResource.KEY + ":url", "git-admin");
	}

	@Test
	public void checkStatusInvalidIndex() {
		httpServer.stubFor(get(urlPathEqualTo("/"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html>some</html>")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), GitPluginResource.KEY + ":url", "git-admin");
	}

	@Test
	public void checkStatusGitProtocol() {
		em.createQuery("UPDATE ParameterValue SET data=:data WHERE parameter.id=:parameter")
				.setParameter("data", "git://any").setParameter("parameter", "service:scm:git:url").executeUpdate();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void checkStatusNoIndex() {
		em.createQuery("UPDATE ParameterValue SET data=:data WHERE parameter.id=:parameter")
				.setParameter("data", Boolean.FALSE.toString()).setParameter("parameter", "service:scm:git:index")
				.executeUpdate();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void findAllByName() throws Exception {
		prepareMockAdmin();
		httpServer.start();

		final List<NamedBean<String>> projects = resource.findAllByName("service:scm:git:dig", "as-");
		Assertions.assertEquals(4, projects.size());
		Assertions.assertEquals("has-evamed", projects.get(0).getId());
		Assertions.assertEquals("has-evamed", projects.get(0).getName());
	}

}
