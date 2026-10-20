export default defineAppConfig({
  ui: {
    colors: {
      primary: 'cyan',
      neutral: 'slate'
    },
    button: {
      slots: {
        base: 'font-display'
      }
    },
    card: {
      slots: {
        root: 'rounded-[20px]'
      }
    }
  }
})
