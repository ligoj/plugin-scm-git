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
import { h } from 'vue'
import { VBtn, VChip, VIcon, useI18nStore } from '@ligoj/host'

const PARAM_URL = 'service:scm:git:url'
const PARAM_REPO = 'service:scm:git:repository'

/** Repository home link. Mirrors the legacy renderFeaturesScm('git'). */
function renderFeatures(subscription) {
  const params = subscription?.parameters
  const url = params?.[PARAM_URL]
  const repo = params?.[PARAM_REPO]
  if (!url || !repo) return []
  const { t } = useI18nStore()
  return [
    h(
      VBtn,
      {
        icon: true,
        size: 'small',
        variant: 'text',
        title: t('service:scm:git:repository'),
        href: `${url.replace(/\/$/, '')}/${repo}`,
        target: '_blank',
        rel: 'noopener noreferrer',
      },
      () => h(VIcon, { size: 'small' }, () => 'mdi-git'),
    ),
  ]
}

/** Repository chip. Mirrors the legacy renderKey('service:scm:git:repository'). */
function renderDetailsKey(subscription) {
  const repo = subscription?.parameters?.[PARAM_REPO]
  if (!repo) return null
  const { t } = useI18nStore()
  return h(
    VChip,
    { size: 'small', variant: 'tonal', class: 'mr-1', title: t('service:scm:git:repository') },
    () => [h(VIcon, { start: true, size: 'small' }, () => 'mdi-git'), ' ', String(repo)],
  )
}

export default { renderFeatures, renderDetailsKey }
