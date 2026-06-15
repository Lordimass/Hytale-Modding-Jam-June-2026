# `Blackout`
By PERL

> You hit the ground, but don't stop falling. After what feels like an eternity, you hit the ground, hard. Looking up, you're in an office you've never seen before.. yet it feels familiar. The only thing you do know for certain is that you're now stuck. A dark, gltiching mass is standing before you. As the lights remain on, the entity appears to remain stagnate. Well, entities. As you walk around, almost every corner reveals another entity... standing there... watching... *waiting*
> 
> You push together supplies, get ready to fortify your position for whatever they may be preparing. Then suddenly, it all goes dark. That's when the unearthly screams started. 

In `Blackout`, you are trapped in the backrooms with intermitten waves (think of it like the Infinite Ikea) where the entities attack at night and are passive and immortal by day. Your goal is to progress through 4 zones of the backrooms (office -> pool -> apartments -> garage) and eventually escape. The barrier between rooms requires a key that must be fabricated from a machine. The machine, however, can take upwards of ~5 minutes to finish the fabrication process (assuming you have the resources necessary) - during which you must craft turrets, walls, and foritifcations to ensure the entity does not stop the machine before it's done. 

As you progress through the rooms, the way through to the next gets more and more complicated until finally... *freedom*

---
Hytale mod source code for the mod created by Paralaxe, Ev0, Riprod, and Lordimass for the Hytale Modding mod jam in June 2026.


---
# Getting Starts (developing)

1. Init Gradle `./gradlew generateVSCodeConfig`
2. Run server `./gradlew runServer`

If you are on windows, use `./gradlew.bat` instead

The project is split into 4 parts
1. Defensive - hold all of the logic for the turrets, defenses, etc
2. Offensive - hold all of the logic for the enemies, npcs, and spawning
3. Progression - hold all of the logic for the progression
4. World - hold all of the world logic and gridwave setup

Each of these are treated as their own plugins. So go crazy, just stay in your folder!