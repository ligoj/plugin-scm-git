/*
 * Service layer for plugin "scm-git".
 *
 * Tool-level plugin (lives at `service:scm:git`). The parent `plugin-scm`
 * delegates the subscription-row hooks to us via its `subPluginIdFor`
 * delegation. Mirrors the legacy `git.js` + the shared
 * `scm.js#renderFeaturesScm`:
 *
 *   - renderFeatures   → a home link to the repository
 *     (`url + '/' + repository`).
 *   - renderDetailsKey → the repository chip
 *     (`service:scm:git:repository`).
 *
 * Kept free of Vue SFC imports so it can be unit-tested without a DOM.
 */
import { renderServiceLink, renderDetailsChip, useI18nStore } from '@ligoj/host'

const PARAM_URL = 'service:scm:git:url'
const PARAM_REPO = 'service:scm:git:repository'

/** Repository home link. Mirrors the legacy renderFeaturesScm('git'). */
function renderFeatures(subscription) {
  const params = subscription?.parameters
  const url = params?.[PARAM_URL]
  const repo = params?.[PARAM_REPO]
  if (!url || !repo) return []
  const { t } = useI18nStore()
  return [renderServiceLink({ icon: 'mdi-git', href: `${url.replace(/\/$/, '')}/${repo}`, title: t('service:scm:git:repository') })]
}

/** Repository chip. Mirrors the legacy renderKey('service:scm:git:repository'). */
function renderDetailsKey(subscription) {
  const repo = subscription?.parameters?.[PARAM_REPO]
  if (!repo) return null
  const { t } = useI18nStore()
  return renderDetailsChip({ icon: 'mdi-git', text: repo, title: t('service:scm:git:repository') })
}

export default { renderFeatures, renderDetailsKey }
