// Client check only (runDifficultyValidation -PvalidationChoice=nightmare): a third difficulty from a script,
// with its own test datapack and card frame (pathbound_test:card_nightmare from kubejs/assets).
PathboundEvents.register(e => {
  e.add('nightmare', d => d
    .name({ en_us: 'Nightmare', de_de: 'Albtraum' })
    .description({ en_us: 'A difficulty added by a KubeJS startup script, with its own frame.' })
    .color('#FF5050')
    .icon('minecraft:wither_rose')
    .order(2)
    .stage('pathbound_nightmare')
    .datapack('builtin:test_nightmare')
    .frame('pathbound_test:card_nightmare'))
})
