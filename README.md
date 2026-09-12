# Trip ETA

A [RuneLite](https://runelite.net/) plugin that estimates when your inventory will
fill, counting only the time you are actually gathering.

Woodcutting now. Mining and fishing next.

## What it shows

A small overlay while a trip is in progress:

- **Logs** gathered this trip, out of what the inventory (plus your log basket, if you
  tell it about one) can still take.
- **Chopping left**: how much more chopping it will take to fill up, as a range that
  tightens as the trip goes on.
- **Off tree**: how long you have not been chopping, shown only while that is true.
- A progress bar.

## How the estimate works

Most "time until full" numbers are wall-clock guesses, and wall-clock is the wrong
clock. Trees fall, spots move, you walk, you look away. None of that says anything
about how fast logs arrive once you are chopping.

So the plugin keeps two clocks:

- **Gathering time**: ticks where your character is in the chopping animation. Logs
  per gathering tick is a stable rate, and it is the only thing the estimate uses.
- **Time off the tree**: everything else. It is displayed, and it is reported to Dink
  if you ask for that, but it never moves the estimate.

The estimate is `items still needed × seconds of chopping per item`. The rate comes
from this trip, blended with what earlier trips on this account taught it, so the
first minute of a trip is not wild. The range reflects how many logs the rate is
based on: wide after two logs, narrow after twenty.

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

## Log basket

Set **Extra capacity (log basket)** to how many logs your basket holds (28 for a
full one, or whatever is left after you keep some). The plugin then counts a log
that arrives without taking an inventory slot as having gone into an open basket,
treats a large inventory drop away from a bank as you filling the basket by hand,
and resets when it sees "You empty your basket into the bank."

## Privacy

Nothing is sent anywhere by this plugin. No files, no network. The only thing it
stores is the learned chopping rate, in your RuneLite profile settings. If you turn
on the Dink options, Dink does the sending, under Dink's own settings.

## Development

```
./gradlew test    # unit tests for the estimator
./gradlew run     # development client with the plugin loaded
```
