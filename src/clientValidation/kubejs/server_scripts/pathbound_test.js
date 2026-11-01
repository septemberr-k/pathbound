// Client check only: refuse hard, mark the player after a choice, log joins.
PathboundEvents.beforeChoose(e => {
  if (e.difficulty == 'hard') e.cancel()
})
PathboundEvents.afterChoose(e => {
  if (e.player) e.player.addTag('kjs_after_choose_' + e.difficulty)
  console.info('pathbound afterChoose ' + e.difficulty + ', world difficulty now ' + Pathbound.get(e.server))
})
PathboundEvents.playerJoin(e => console.info('pathbound playerJoin ' + e.player.username + ' ' + e.difficulty))

// Recipes per difficulty: each applies only in worlds with that difficulty (checked with -Dpathbound.expectRecipes).
PathboundEvents.recipes('nightmare', e => {
  e.shapeless('minecraft:diamond', ['minecraft:dirt', 'minecraft:dirt']).id('pathbound_test:nightmare_diamond')
})
PathboundEvents.recipes('normal', e => {
  e.shapeless('minecraft:coal', ['minecraft:dirt']).id('pathbound_test:normal_coal')
})
