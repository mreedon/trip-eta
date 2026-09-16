# Trip ETA

A [RuneLite](https://runelite.net/) plugin that estimates when your inventory will
fill, counting only the time you are actually gathering.

Woodcutting, mining and fishing. Each is an entry in one activity table: the
animation set that means "gathering" and the chat line that means "an item arrived".

## Who it is for

Long trips you are not watching. Redwoods, Motherlode, amethyst, a fishing spot you
sit at for twenty minutes. That is where "how much more chopping is left" is worth
knowing, and where a warning a couple of minutes before full lets you look back at
the right moment.

It has nothing to offer three things:

- **Fast trips.** A load that fills in a couple of minutes is over before the estimate
  settles, since it wants a few items and a learned rate first.
- **Tick manipulation.** The trick is cancelling the gathering animation every tick,
  and that animation is the only clock the plugin trusts.
- **Anything that never fills.** Power-mining iron onto the floor keeps you at a full
  inventory forever, so there is no fill left to predict.

The last of those it handles rather than gets wrong. When a full inventory only ever
frees a few slots at a time, the plugin marks the trip full, goes quiet, and stays that
way until you bank. Emptying the lot at once is a different thing entirely, whether
that is the Motherlode hopper or a load of amethyst going to bolt tips, and the trip
carries on toward the next fill.

## What has been tested

Watched in game, with each estimate checked against what the trip actually took:

- **Redwoods**, with a log basket and forester's rations. Most of the estimator's
  calibration comes from this, several hundred trips of it.
- **Motherlode Mine**, including emptying into the hopper part way through a trip.
- **Karambwan**, with a fish barrel.

Everything else runs on the same two signals, so other trees, rocks and fishing spots
should work untouched. They have not been watched, though, and the honest failure mode
is a missing animation: the count climbs while the estimate never appears, because
nothing is being recognised as gathering. The plugin logs the animation number it did
not know in that case, and adding it is a one-line fix. Open an issue with the number
and the method.

## What it shows

A small overlay while a trip is in progress:

- **Logs**, or ores, or fish: how many you have gathered toward this load, out of what
  it takes to fill. Tools you are carrying do not count toward either number.
- **Chopping left**: how much more chopping it will take to fill up, as a range that
  tightens as the trip goes on.
- **Off tree**: how long you have not been chopping, shown only while that is true.
- **Basket** or **Barrel**, when you are carrying one that takes what you are gathering.
- A progress bar, showing the same fraction as the count.

## How the estimate works

Most "time until full" numbers are wall-clock guesses, and wall-clock is the wrong
clock. Trees fall, spots move, you walk, you look away. None of that says anything
about how fast logs arrive once you are chopping.

So the plugin keeps two clocks:

- **Gathering time**: ticks where your character is in the chopping animation. Logs
  per gathering tick is a stable rate, and it is the only thing the estimate uses.
- **Time off the tree**: everything else. It is displayed, and it is reported to Dink
  if you ask for that, but it never moves the estimate.

Some pauses are part of the work rather than time away: the hop to the next rock when
one runs dry, a fishing spot moving a few tiles. Once you are back at it, a pause that
short is credited to gathering time, and the off-rock line never lit up for it. A pause
that runs longer is time away, all of it. Woodcutting gets no such allowance: a tree
keeps you chopping log after log, so a pause there is you.

The estimate is `items still needed × seconds of chopping per item`. The rate comes
from this trip, blended with what earlier trips taught it, so the first minute is not
wild.

What it learns is kept per item type, not per skill. A redwood and a willow are both
woodcutting and nothing alike, and Motherlode is not iron. So the rate is stored under
the thing you are gathering, and a type with a history of its own anchors the estimate
firmly. A type it has never seen falls back on the skill's general rate at a quarter of
the weight: enough that a first trip at a new tree is sane, light enough that a handful
of real chops outweighs it. A load that came out as two different items teaches neither,
since one gathering clock cannot be divided between them.

The range carries two kinds of uncertainty. How well the rate is known, which shrinks
as the trip goes on. And plain luck: even with the rate known exactly, the wait for the
next five logs is far less certain, relatively, than the wait for the next thirty,
because each log is its own dice roll. The per-roll odds fall out of the measured rate
and the game's roll cadence, and the band is built from them. That is why the range
gets wider in relative terms near the end of a trip: it is telling the truth about the
last few logs. The band is lopsided on purpose, stretching further above the estimate
than below, since a run of bad luck can take much longer than a run of good luck can
save.

The width is not taken on faith. Replaying several hundred banked trips showed the
textbook figure holding about three outcomes in four rather than the four in five it
claimed, by much the same margin whether four logs remained or twenty, so the constant
carries that correction rather than the model pretending its shape is exact.

### Clean cuts

With a felling axe and forester's rations, one successful chop in five is a "clean
cut" that yields no log. Two different random things are going on there, and the
plugin keeps them apart:

- **How often a chop succeeds** depends on your level, axe and tree. That is what the
  trip measures, and every success counts, log or not.
- **Whether a success hands over a log** is a fixed one-in-five coin with no memory.
  Three clean cuts in a row say nothing about the next chop, so the plugin applies
  the known 80% instead of re-guessing it from a handful of flips.

The upshot is that a run of clean cuts does not push the estimate out the way a
silent stretch of chopping would. It is read as "the tree is giving successes, the
coin was unlucky", which is what actually happened.

Rations run out, and the plugin notices without being told. Twenty chops with no clean
cut would happen about once in eighty trips if the coin were still in play, so a run
that long retires it until one shows up again.

## Notifications

- **Warn this long before full** (default 2 minutes): once the remaining chopping
  time drops below this, you get a RuneLite notification. Because the estimate only
  counts chopping time, the warning means "two more minutes of chopping", which is
  what you want to know when you are looking at a second monitor.
- **Also notify when full**: off by default, since the game already says so.
- **Send through Dink**: hands the same notification to the
  [Dink](https://github.com/pajlads/DinkPlugin) plugin, which can post it to
  Discord or anywhere else Dink is pointed at. Turn on *External Plugin
  Notifications* in Dink's settings for this to do anything. The Dink message
  carries the raw trip numbers in its `metadata` block for anyone running their own
  webhook handler.
- **Dink trip summary**: when a trip ends at the bank, send its totals: logs,
  chopping time, time off the tree, clean cuts.

## Log basket and fish barrel

No setup. Carry a log basket, a forestry basket, a fish barrel or a fish sack barrel,
in your inventory or worn, open or closed, and the plugin adds its 28 slots to the trip
and follows what is in it:

- an item that arrives without taking an inventory slot went into an open container,
- a large inventory drop away from any bank is you filling it by hand,
- "The basket is full", "Your barrel is empty", and the emptied-to-bank and
  emptied-to-inventory lines set the count directly.
- Right-click *Check* opens an item box rather than printing to chat; the plugin
  reads that box when it follows a Check click.

Each only counts for what it actually holds. A log basket adds nothing to a mining
trip, and your ore fills the inventory as usual. Carrying both a basket and a barrel is
fine: the one that takes what you are gathering is the one that counts.

After logging in the contents are unknown and assumed empty. With an **open** container
that fixes itself: an open one takes every item until it is full, so the first item that
lands in your inventory proves it is at 28 and the count snaps there. With a closed one
the plugin cannot tell, so right-click it and *Check* once and it picks up the real
number.

Coal bags and gem bags are not followed.

## Privacy

Nothing is sent anywhere by this plugin. No files, no network. The only thing it
stores is the learned rates, one per thing you gather, in your RuneLite profile
settings. If you turn on the Dink options, Dink does the sending, under Dink's own
settings.

## Development

```
./gradlew test    # unit tests for the estimator
./gradlew run     # development client with the plugin loaded
```
