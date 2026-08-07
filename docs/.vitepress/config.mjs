import { defineConfig } from 'vitepress'

const repository = 'https://github.com/andreasjhagen/Blockfolk-NPC'

export default defineConfig({
  title: 'Blockfolk',
  description: 'Documentation for the Blockfolk NPC system for Paper servers.',
  base: process.env.DOCS_BASE ?? '/Blockfolk-NPC/',
  cleanUrls: true,
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', href: `${process.env.DOCS_BASE ?? '/Blockfolk-NPC/'}icon.png` }],
    ['meta', { name: 'theme-color', content: '#d97706' }]
  ],
  themeConfig: {
    nav: [
      { text: 'Guide', link: '/guide/getting-started' },
      { text: 'Features', link: '/features/npcs' },
      { text: 'Reference', link: '/reference/commands' },
      { text: 'GitHub', link: repository }
    ],
    sidebar: [
      {
        text: 'Introduction',
        items: [
          { text: 'Overview', link: '/' },
          { text: 'Getting started', link: '/guide/getting-started' },
          { text: 'Core concepts', link: '/guide/core-concepts' }
        ]
      },
      {
        text: 'Features',
        items: [
          { text: 'NPCs & instances', link: '/features/npcs' },
          { text: 'Customization & equipment', link: '/features/customization' },
          { text: 'Behaviour routines', link: '/features/behaviours' },
          { text: 'Combat', link: '/features/combat' },
          { text: 'Routes', link: '/features/routes' },
          { text: 'Locations', link: '/features/locations' },
          { text: 'AI behaviour', link: '/features/ai-behaviour' },
          { text: 'AI request context', link: '/reference/ai-request-context' }
        ]
      },
      {
        text: 'Reference',
        items: [
          { text: 'Commands & permissions', link: '/reference/commands' },
          { text: 'config.yml', link: '/reference/configuration' },
          { text: 'Data & backups', link: '/reference/data-storage' }
        ]
      },
      {
        text: 'Integrations',
        items: [
          { text: 'BeautyQuests', link: '/integrations/beautyquests' }
        ]
      }
    ],
    search: { provider: 'local' },
    outline: { level: [2, 3] },
    editLink: {
      pattern: `${repository}/edit/main/docs/:path`,
      text: 'Edit this page on GitHub'
    },
    socialLinks: [{ icon: 'github', link: repository }],
    footer: {
      message: 'Blockfolk documentation',
      copyright: 'Built with VitePress'
    }
  }
})
