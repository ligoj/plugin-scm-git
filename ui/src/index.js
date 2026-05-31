/*
 * Plugin "scm-git" — Git implementation of plugin-scm.
 *
 * Tool-level plugin (`service:scm:git`). Augments the parent
 * `plugin-scm` via i18n parameter labels + row features (home link +
 * resource chip) merged in through plugin-scm's `subPluginIdFor`
 * delegation hook.
 *
 * Authored as source — compiled to `/main/scm-git/vue/index.js` by Vite.
 */
import { useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {
  renderFeatures: service.renderFeatures,
  renderDetailsKey: service.renderDetailsKey,
}

export default {
  id: 'scm-git',
  label: 'Git',
  requires: ['scm'],
  install() {
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "scm-git" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-git', color: 'orange-darken-3' },
}

export { service }
