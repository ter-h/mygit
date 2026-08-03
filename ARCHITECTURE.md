# MiniGit Architecture

This document explains each component in detail — what it does, how it's implemented, and how it fits together with the rest of the system. It assumes you've read [README.md](README.md) for the user-facing command overview.

## The big picture

Every time you run `java -cp out com.minigit.MiniGit <command>`, a brand new JVM process starts, does its work, and exits. **Nothing is kept in memory between commands** — every piece of state that needs to survive is written to a file under `.git/`, and every command starts by reading whatever it needs from those files. This is true of real Git too: there's no background daemon holding state between `git add` and `git commit`. `.git/` *is* the state machine; the Java objects (`Repository`, `Index`, `ObjectStore`, `Config`) are just an in-memory lens onto it for the duration of one command.

```
MiniGit (dispatcher)
   │
   ├── InitCommand
   ├── AddCommand      ─┐
   ├── CommitCommand    │  each of these builds a
   ├── LogCommand       │  Repository and works
   ├── StatusCommand    │  through it
   └── ConfigCommand   ─┘
             │
             ▼
        Repository
        ├── ObjectStore   (.git/objects/)
        ├── Index         (.git/index)
        └── Config        (.git/config)
```

---

## `MiniGit` — the entry point

`src/com/minigit/MiniGit.java`

`main(String[] args)` does three things:
1. If there are no arguments, prints usage and exits.
2. Splits `args` into the command name (`args[0]`) and the remaining arguments (`commandArgs`), which get handed to whichever command runs.
3. A `switch` on the command name picks a concrete `Command` implementation, constructs it, and calls `.execute(commandArgs)`.

```java
switch (command) {
    case "init":   new InitCommand().execute(commandArgs);   break;
    case "add":    new AddCommand().execute(commandArgs);    break;
    case "commit": new CommitCommand().execute(commandArgs); break;
    case "log":    new LogCommand().execute(commandArgs);    break;
    case "status": new StatusCommand().execute(commandArgs); break;
    case "config": new ConfigCommand().execute(commandArgs); break;
    default: /* unknown command message + usage */
}
```

The whole `main` body is wrapped in a `try`/`catch (Exception e)` that prints `"Error: " + e.getMessage()` plus a stack trace — a catch-all so no command crashes with a bare, unhandled exception dump.

**Design note:** each `case` calls `.execute(...)` directly on a freshly-constructed concrete class (`new InitCommand().execute(...)`), not through a `Command`-typed variable. This means the `Command` interface isn't currently doing any real polymorphic dispatch — it's a compile-time contract (every command class must expose `execute(String[]) throws Exception`), not something the switch statement actually leverages. It would start to matter if the dispatch were ever rewritten as data-driven, e.g. a `Map<String, Command>` looked up by command name instead of a hardcoded switch.

---

## `Command` — the shared interface

`src/com/minigit/commands/Command.java`

```java
public interface Command {
    void execute(String[] args) throws Exception;
}
```

One method. Every command class (`InitCommand`, `AddCommand`, `CommitCommand`, `LogCommand`, `StatusCommand`, `ConfigCommand`) implements this. `args` here is always *only* the arguments after the command name — `MiniGit.main` already stripped that off.

---

## `Repository` — the entry point into a repo

`src/com/minigit/core/Repository.java`

Every command's first two lines are almost always:

```java
Path base = Paths.get(System.getProperty("user.dir"));
Repository repo = new Repository(base);
```

`Repository`'s constructor takes the working directory and derives everything else from it:

```java
public Repository(Path workingDir) {
    this.workingDir = workingDir;
    this.gitDir = workingDir.resolve(".git");
    this.objectStore = new ObjectStore(gitDir);
    this.index = new Index(gitDir);
    this.config = new Config(gitDir);
}
```

So constructing a `Repository` also constructs an `ObjectStore`, `Index`, and `Config`, all rooted at the same `.git` directory. Commands reach these through getters: `getObjectStore()`, `getIndex()`, `getConfig()`, `getWorkingDir()`.

Beyond that, `Repository` knows how to answer "what commit are we on":

- **`getCurrentCommitHash()`** — reads `.git/HEAD` (which contains a line like `ref: refs/heads/main`), resolves that to the actual ref file (`.git/refs/heads/main`), and reads the commit hash out of it. Returns `null` if there's no commit yet (fresh repo, ref file doesn't exist).
- **`updateHEAD(String commitHash)`** — writes a new hash into the current branch's ref file. This is what "advancing a branch" means in practice: `HEAD` itself almost never changes (it just says *which* branch you're on); the branch ref file is what gets overwritten on every commit.

**Detached-HEAD state is not supported** — `Repository` always assumes `HEAD` points at a branch, never directly at a commit. Nothing in MiniGit creates a detached-HEAD state, so this was a deliberate scope cut, not an oversight.

---

## `ObjectStore` — content-addressable storage

`src/com/minigit/core/ObjectStore.java`

This is the foundation everything else is built on. The core idea, straight from real Git: **don't store files, store content — and name each piece of content after the hash of its own bytes.** Identical content always produces the identical hash, so the same content is never stored twice, and content can never silently change without its hash changing too.

### Object format

Every object (blob, tree, or commit) is stored as:

```
<type> <byte-length>\0<content>
```

For example, storing the file content `hello\n` (6 bytes) as a blob produces the literal bytes:

```
blob 6\0hello\n
```

The whole thing (header + content) gets SHA-1 hashed, and the hash becomes the object's identity and filename. There is no compression — unlike real Git (which zlib-deflates objects), you can `xxd` a MiniGit object file and read the header and content directly.

### Where objects live on disk

`objectsDir = gitDir.resolve("objects")`. Given a hash like `8f2383152420af92751729e1c7c01ac5a62788b8`, the object is stored at:

```
.git/objects/8f/2383152420af92751729e1c7c01ac5a62788b8
```

— first 2 hex characters as a subdirectory, remaining 38 as the filename. This is exactly real Git's layout, and exists so that no single directory ends up with hundreds of thousands of files in it.

### Hashing

```java
public static String sha1(byte[] data)
```

Uses `java.security.MessageDigest` with algorithm `"SHA-1"`, then hex-encodes the resulting bytes (`bytesToHex`) into the familiar 40-character hash string.

### Building the header+content bytes once, shared by hash-only and hash-and-store paths

```java
private static byte[] buildFullData(String type, byte[] content)
```

Builds `"<type> <length>\0" + content` as a single byte array. Two public methods build on top of this:

```java
public static String hashObject(String type, byte[] content) throws IOException
```
Hashes the content **without writing anything to disk**. Used by `StatusCommand` to answer "what would this file's blob hash be right now" without polluting the object store just from looking around.

```java
public String storeBlob(byte[] bytes) throws IOException
```
Builds the full data, hashes it, creates the subdirectory if needed, and writes the bytes to `objects/<xx>/<rest>`. Returns the hash.

### Reading objects back

```java
public static RawObject extractRawObject(byte[] fullData)
```
Given raw bytes read from an object file, scans forward byte-by-byte to find the `\0` null terminator, splits everything before it into a header string (parsed into `type` + `size`), and everything after it into the raw `content` bytes.

```java
public RawObject readObject(String hash) throws IOException
```
Reconstructs the file path from the hash, reads the bytes, delegates to `extractRawObject`. `RawObject` is a small holder (`type`, `content`) — it deliberately doesn't interpret what the content *means*. Interpreting a commit's headers or a tree's entries is left to callers (`LogCommand`, `StatusCommand`), keeping "storage" and "interpretation" as separate concerns.

### Trees — directory snapshots

```java
public static class TreeEntry {
    public final String name;  // bare filename, e.g. "a.txt"
    public final String type;  // "blob" (no nested-tree support currently exists)
    public final String hash;  // hash of that file's blob
}
```

```java
public String storeTree(List<TreeEntry> entries) throws IOException
```

Builds content as one line per entry — `"name type hash\n"` — concatenated together, then hashes and stores it exactly like a blob, just with header type `"tree"` instead of `"blob"`. Example content for two files:

```
a.txt blob 8f2383152420af92751729e1c7c01ac5a62788b8
b.txt blob 04d0985b8302976f97a5c9fda9b4b4b9e029b0fe
```

```java
public List<TreeEntry> readTree(String treeHash) throws IOException
```

The reverse: reads the tree object, splits its content into lines, and splits each line by spaces back into a `TreeEntry`. This is what lets `StatusCommand` know what was actually committed, not just what's staged.

**Limitation:** since `TreeEntry.name` is only ever a bare filename (never a path with subdirectories), MiniGit has no concept of nested directories. Every file in a repo lives flat at the root.

### Commits

```java
public String storeCommit(String treeHash, String parentHash, String author, String message) throws IOException
```

Builds content as:

```
tree <treeHash>
[parent <parentHash>]      <- omitted entirely if parentHash is null (the first commit)
author <author>
committer <author>          <- same value as author, MiniGit doesn't distinguish them

<message>
```

Note the **blank line** separating headers from the message — this is what lets `LogCommand`/`StatusCommand` split header lines from the message by finding the first `"\n\n"`.

Example raw commit content (as you'd see with `xxd`):

```
tree b3be957a6e65b7cdf719071a4fb1d88df1acfd6e
author Jane Doe <jane@example.com>
committer Jane Doe <jane@example.com>

initial commit
```

If `parentHash` is `null` (there is no parent — this is the very first commit), the `parent` line is omitted entirely, not left blank. This is what lets `LogCommand` know when to stop walking backward.

---

## `Index` — the staging area

`src/com/minigit/core/Index.java`

Persisted at `.git/index`, but as simple CSV lines (`path,hash,timestamp`) rather than Git's real binary index format — consistent with this project's general "simplified, not-a-byte-for-byte-clone" approach.

```java
Map<String, IndexEntry> entries;   // keyed by path string (relative to repo root)
```

```java
public static class IndexEntry {
    public final Path path;
    public final String hash;
    public final long timeStamp;
}
```

`IndexEntry` carries its own `path` field even though it also happens to be the map's key — this matters because iterating `entries.values()` (e.g. in `CommitCommand`, building a tree) only gives you the value objects, not the keys. Without a `path` field, an `IndexEntry` on its own wouldn't know what file it belonged to.

- **`load()`** — clears the in-memory map, then (if `.git/index` exists) parses each line by splitting on `,` into path/hash/timestamp and repopulates the map.
- **`save()`** — writes `entries.values()` back out as CSV lines, one per entry.
- **`add(Path path, String hash)`** — builds a new `IndexEntry` with the current time (`System.currentTimeMillis()`) and puts it into the map, keyed by `path.toString()`. Re-adding the same path overwrites the existing entry — this is exactly `git add`'s real semantics: staging a changed file replaces what was staged before, it doesn't add a second entry.
- **`clear()`** — empties the map. **Not currently called anywhere** — an earlier version of `CommitCommand` called this after every commit, but that was based on a wrong mental model (see the `CommitCommand` section below) and was removed. Kept as a building block for a possible future `reset`/unstage command.

---

## `Config` — repository configuration

`src/com/minigit/core/Config.java`

Persisted at `.git/config` as simple `key=value` lines — again, a deliberate simplification versus real Git's INI `[section]` format, matching `Index`'s CSV-not-binary philosophy.

```java
Map<String, String> values;
```

- **`load()`** — clears the map, then (if the file exists) splits each line on the first `=` into key/value.
- **`save()`** — writes `"key=value"` lines back out from `values.entrySet()`.
- **`set(key, value)` / `get(key)`** — trivial map operations, no file I/O (persistence only happens on `save()`).

`user.name` and `user.email` are the only keys MiniGit currently reads (in `CommitCommand`), but the format is generic — any `key=value` pair can be stored.

---

## The commands, in detail

### `InitCommand`

Creates the `.git` layout: `objects/`, `refs/heads/`, `refs/tags/`, `HEAD` (containing `ref: refs/heads/main\n`), and empty `config`/`index` files. Guards against clobbering an existing repo: if `.git` already exists, it prints `"Reinitialized existing Git repository in ..."` and returns immediately, without touching `HEAD`/`config`/`index` (which would otherwise silently reset the current branch pointer and wipe staged files).

### `ConfigCommand`

```
minigit config <key> <value>
```

Guards `args.length < 2` with a usage message (avoids a raw `ArrayIndexOutOfBoundsException`). Otherwise: `repo.getConfig().load()` → `set(key, value)` → `save()`. Loading before setting matters — otherwise setting `user.name` would wipe out a previously-set `user.email`, since `save()` writes out the *entire* in-memory map, not just the one key that changed.

### `AddCommand`

```
minigit add <files...>
```

For each argument:
1. Resolves it to an absolute path (`Path.of(p).toAbsolutePath()`) — so it can find the file regardless of exactly how the path was typed.
2. Relativizes that absolute path against `repo.getWorkingDir()` to get a path relative to the repo root — this relative form is what gets used as the index key.
3. Reads the file's bytes, stores them as a blob (`storeBlob`), and stages the (relative path → hash) pair via `Index.add`.

After the loop, `index.save()` is called **once** — not inside the loop — so staging N files results in one write to `.git/index`, not N.

**Why relative paths matter:** the index needs a *consistent* path format regardless of how a file was staged, because `StatusCommand` later needs to look up the exact same key when walking the working directory. Early in this project's development, `Index` stored whatever string was typed on the command line (sometimes relative, sometimes absolute) while `StatusCommand`'s directory walk always produced absolute paths — causing staged files to be silently unmatched and misreported as untracked or modified. Normalizing both sides to repo-root-relative paths fixed this.

### `CommitCommand`

```
minigit commit -m "message"
```

1. Loads the index.
2. Builds a `List<TreeEntry>` from `index.getEntries().values()` — one entry per staged file, using its bare filename (`entry.path.getFileName().toString()`), type `"blob"`, and its staged hash.
3. Stores that list as a tree (`storeTree`) → `treeHash`.
4. Gets the parent commit hash via `repo.getCurrentCommitHash()` (naturally `null` for the first commit).
5. Loads config, reads `user.name`/`user.email`, builds an author string (`"name <email>"`), falling back to placeholder pieces (`"Unknown"` / `"unknown@example.com"`) individually if either key was never set — this avoids a `NullPointerException` from string-concatenating a `null`.
6. Stores the commit (`storeCommit`) → `commitHash`.
7. Advances the branch: `repo.updateHEAD(commitHash)`.

**Important:** the index is *not* cleared after a commit. An earlier version of this command did call `index.clear()`/`index.save()` here, reasoned as "nothing left to stage once it's committed" — but that reasoning conflates two different mental models of what an index is:

- **Wrong model:** the index holds *pending changes* — once committed, it's empty until the next thing you stage.
- **Correct model (real Git's):** the index holds the *entire current snapshot* of every tracked file. A commit just freezes whatever's in the index right now; it doesn't consume or empty it.

Under the wrong model, any file committed once and never explicitly re-`add`ed again would silently vanish from every subsequent commit's tree — a real, serious bug (confirmed via a multi-commit regression test: commit 1 staged `a.txt` + `b.txt`; only `a.txt` was re-staged before commit 2; commit 2's tree ended up containing only `a.txt`, dropping `b.txt` entirely even though nothing about it had changed). Removing the `clear()`/`save()` calls fixed it — now the index persists exactly like real Git's does, and a file that's never modified just stays staged indefinitely.

### `LogCommand`

```
minigit log
```

Starts at `repo.getCurrentCommitHash()`. If `null`, prints `"No commits yet"` and stops. Otherwise, loops:

1. Read the commit object at `currentHash`.
2. Split its content on the first `"\n\n"` into `header` and `message`.
3. Split `header` into lines, and each line into a key/value pair (splitting on the first space) — looking specifically for a `parent` line.
4. Print the commit hash and message.
5. Set `currentHash` to whatever `parent` value was found (or `null` if there wasn't one), which either continues the loop or ends it.

Since every commit in MiniGit has at most one parent (no merge commits), this is effectively a linear walk backward through history, even though the general shape (accumulate header key/values, follow one "next" pointer) would extend naturally to a real graph traversal if merges were ever added.

### `StatusCommand`

```
minigit status
```

This is the most involved command — it compares *three* different views of "what files exist," not two:

1. **Staged** — `repo.getIndex().load()` → `getEntries()`, keyed by repo-root-relative path.
2. **Committed** — if there's a current commit, its commit object is read, the `tree` line is parsed out of its header (same header-parsing approach as `LogCommand`), and `readTree(treeHash)` gives back the list of files in the last commit. This is built into a `Map<String, String>` of `filename → hash` (keyed by bare filename, since `TreeEntry.name` never contains subdirectory paths).
3. **Working directory** — `Files.walk(repo.getWorkingDir())`, filtered to regular files and anything not under `.git`.

For each file found while walking the working directory:

- **Relativize** its path against `workingDir` first, and look it up in the **staged** map using that relative path (matching `AddCommand`'s key format).
  - **Found** → compare the file's current on-disk hash (via `ObjectStore.hashObject`, no write) against the staged hash: equal → `"staged, unchanged"`, different → `"modified (staged)"`.
- **Not staged** → fall back to the **committed** map, looked up by bare filename.
  - **Found**, hash matches current content → `"unchanged"`.
  - **Found**, hash differs → `"modified (not staged)"`.
  - **Not found in either** → `"untracked"`.

Staged status always takes priority over committed status — if a file is both staged and committed, its staged state is what gets reported (matching real Git: what you're about to commit next matters more than what you committed last time).

**Why reading the committed tree matters:** without it, `status` would only ever compare the index to the working directory — meaning a file you just committed (and haven't re-staged) would incorrectly show as `"untracked"`, because the index has no persistent memory of "this was committed" separate from "this is currently staged." Reading the last commit's tree gives `status` a third, independent source of truth: what's actually been saved into history.

---

## Design principles that recur throughout

- **Immutability of objects.** Once a blob/tree/commit is written to `.git/objects/`, it's never modified — only ever read. This is what makes content-addressing safe: a hash always means the same content, forever.
- **Read-then-write, every command.** Nothing survives in memory between CLI invocations. Every command reads whatever state it needs from `.git/` first, does its work, then writes back whatever changed, before the JVM process exits.
- **Simplified-but-analogous formats.** `.git/index` (CSV) and `.git/config` (`key=value`) aren't Git's real binary/INI formats, but they serve the exact same role and are trivially human-readable — a deliberate trade-off favoring learning over byte-for-byte compatibility.
- **Separation of storage from interpretation.** `ObjectStore` only knows how to store and retrieve raw bytes by hash — it has no idea that a commit's content has a `tree`/`parent`/`author` structure, or that a tree's content is a list of name/type/hash lines. Parsing that structure is left entirely to the commands (`LogCommand`, `StatusCommand`) that need it.
