# GameMargins

A RuneLite plugin that keeps your Grand Exchange trades and buy-limit timers on
[gamemargins.com](https://gamemargins.com) up to date by themselves.

## What it does

- **Live slots.** Your eight Grand Exchange slots, as they fill, on the site's Trades page and in the plugin's side
  panel.
- **Exact buy-limit timers.** Every purchase is noted the moment it fills, so the site and the panel know to the
  second how much of each 4-hour buy limit you have used and when it frees up. The offer setup screen shows the limit
  left for the item you are about to buy.
- **Your flips, logged.** Every buy and sell becomes a trade on the site, paired first in, first out, with the profit
  after the 2% tax, how long buying and selling took, and your buy price against the market at that moment.
- **Notifications.** In the game, when an offer finishes and when a buy limit frees up (each can be switched off in the
  plugin's settings; RuneLite's own notification settings apply).

## What it sends, and when

Nothing is sent until you link the plugin to your gamemargins.com account: press **Link** in the panel, and confirm the
code it shows on `gamemargins.com/link` while logged in there. No password or key is typed into RuneLite; the site
hands the plugin a token for itself, which you can revoke on your account's Security page.

Once linked, the plugin sends, per Grand Exchange slot change: the item, buy or sell, the quantity, the price, what
has filled and when, together with the number the game gives your account (so several accounts stay apart) and your
in-game name. Nothing else from the game: no chat, no inventory, no password. Like any connection to a website, the
address you connect from is visible to the site. Worlds with their own economy (Leagues and other seasonals, Deadman,
tournament, beta, fresh start, PvP Arena, speedrunning, no-save) are ignored.

Events are queued on disk (`.runelite/gamemargins/`) and sent in batches, so nothing is lost while you are offline
or the site is down. What the plugin found when you log in after a while (an offer that finished in the meantime) is
reported as of that moment, so a timer can only run late, never say "free" too early.

Details: the [privacy policy](https://gamemargins.com/privacy).

## Building

Needs a Java 11 JDK. On the development PC it is in `%USERPROFILE%\.jdks\temurin-11` (not on PATH):

```
JAVA_HOME="$USERPROFILE/.jdks/temurin-11" ./gradlew build
```

`build` compiles the plugin and runs the JUnit tests (the offer state machine, the buy-limit windows, the world filter,
the queue).

## Testing in the game before it is on the Plugin Hub

Only a person can test a plugin in the game (RuneLite's rules; automating the game would get the account banned).

1. Start a development RuneLite with the plugin loaded:
   `JAVA_HOME="$USERPROFILE/.jdks/temurin-11" ./gradlew run`
   It opens RuneLite in developer mode. Logging in with a Jagex account in a development client takes one extra step
   the first time: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts.
2. In the plugin's settings (the wrench), leave **Site** at `https://gamemargins.com`, or point it at a local copy of
   the site (`http://localhost:3000`) to test against that.
3. Open the GameMargins panel (the gold G in the sidebar). Press **Link**: the browser opens the site's `/link` page
   with the code the panel shows; log in there and press Link. The panel then says "Synced" after its first batch.
4. What to check:
   - Place a buy offer in the Grand Exchange: within a few seconds it shows in the panel and on
     `gamemargins.com/osrs/trades` (the tab refreshes itself every 10 seconds).
   - Let it fill (or part of it): the panel's **Buy limits** list shows the item with what is left and when it resets;
     the Trades page's Limits tile shows the same.
   - Open the offer setup screen for a buy of the same item: the small GameMargins box under the window says how much
     of the limit is left and when it resets. For an item you haven't bought in 4 hours it says the limit is free.
   - Sell what you bought: the Trades page shows the flip with its profit after tax, how long buying and selling took,
     and your buy price against the market. The panel's **today** figure comes from the site.
   - Cancel an offer that filled nothing: the slot shows "Cancelled" and the trades don't change. Cancel one that
     filled something: the filled part counts.
   - Log out, let an offer finish while logged out (or collect it on mobile), log back in: the plugin reports what it
     found, marked as found at login; the Trades page shows the finished offer and the purchase counts from the login.
   - Hop to a Leagues, Deadman or beta world (when one is up): the panel says the world is not tracked and nothing is
     sent.
   - Unlink on the site (Account › Security): the panel says "Not linked any more" at its next sync and offers Link
     again. Unlink from the panel: the token is forgotten and the queued events dropped.
   - Turn the plugin off and on, close and reopen RuneLite: the slots and timers are still there (the state is on
     disk), and nothing is sent twice (the site checks each event's number).

## Sideloading the built plugin

Without the development client: `./gradlew jar` makes `build/libs/gamemargins-1.0-SNAPSHOT.jar`; copy it into
`~/.runelite/sideloaded-plugins/` (make the folder) and start RuneLite normally. Sideloaded plugins are for
testing only: the Plugin Hub is how players get it.

## Submitting to the Plugin Hub

The Plugin Hub takes only open-source plugins, so this repository must be public first. Then a pull request to
https://github.com/runelite/plugin-hub adds the file `plugins/gamemargins` from `plugin-hub/gamemargins` in this
repository, with `commit=` set to the full hash of the commit to publish. The pull request's description ends with
`Generated-by: Claude Code`. RuneLite's reviewers read the code; the `warning=` line in the manifest is the one they
ask for from every plugin that talks to a third-party server.

## License

BSD 2-Clause, see `LICENSE`.
