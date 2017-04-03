define(function () {
	var current = {

		configureSubscriptionParameters: function (configuration) {
			current.$super('registerXServiceSelect2')(configuration, 'service:scm:git:repository', 'service/scm/git/', null, true, null, false);
		},

		/**
		 * Render Git repository.
		 */
		renderKey: function (subscription) {
			return current.$super('renderKey')(subscription, 'service:scm:git:repository');
		},

		/**
		 * Render Git home page.
		 */
		renderFeatures: function (subscription) {
			return current.$super('renderFeaturesScm')(subscription, 'git');
		}
	};
	return current;
});
