# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

MiniGit is a simplified, educational reimplementation of core Git plumbing in Java. There is no build tool (no Maven/Gradle/Ant) — it's compiled directly with `javac`.

## Commands

Build (compile all sources to `out/`):
```
javac -d out $(find src -name '*.java')
```

Run:
```
java -cp out com.minigit.MiniGit <command> [args...]
```

There are no automated tests in this repository currently.

## Known issue

`src/com/minigit/MiniGit.java` currently fails to compile: the `try` block opened at the top of `main` is never closed before the `switch` statement, but a matching `catch` exists after it. Fix the brace structure before relying on a compiled build.

## Architecture

The codebase mirrors real Git's internal model at small scale:

- **`MiniGit`** (entry point) — dispatches CLI subcommands (`init`, `add`, `commit`, `log`, `status`) to their respective `Command` implementations in `com.minigit.commands`.
- **`Command`** — simple interface (`execute(String[] args)`) implemented by each subcommand class.
- **`Repository`** (`com.minigit.core`) — represents a working repo. Wraps `.git` dir layout (`objects/`, `refs/heads/`, `refs/tags/`, `HEAD`, `config`, `index`), resolves `HEAD` → branch ref → commit hash, and owns an `ObjectStore` and `Index` instance.
- **`ObjectStore`** — content-addressable storage under `.git/objects/<xx>/<rest>`, keyed by SHA-1 of the object bytes. Handles blobs, trees, and commits, each serialized with a `<type> <size>\0<content>` header (git's real object format), and hashed/written via `storeBlob`/`storeTree`/`storeCommit`/`readObject`.
  - Note: `ObjectStore.storeTree` is declared to take `Map<String, String>` (path → hash), but `CommitCommand` currently builds and passes a `Map<String, ObjectStore.TreeEntry>` — these are inconsistent and will not type-check as-is.
- **`Index`** (staging area) — persisted at `.git/index` as simple CSV lines (`path,hash,timestamp`), *not* git's real binary index format. Loaded/saved via `load()`/`save()`; `CommitCommand` reads it to build a tree, then calls `clear()`/`save()` after committing.

### Commit flow (`CommitCommand`)
1. Read `-m <message>` from args.
2. Load `Index` entries → build a tree via `ObjectStore.storeTree`.
3. Look up parent via `Repository.getCurrentCommitHash()`.
4. Write commit object via `ObjectStore.storeCommit`, then `Repository.updateHEAD()` to advance the current branch ref.
5. Clear and save the index.

### Log traversal (`LogCommand`)
Walks the commit graph starting at `HEAD`'s resolved commit, following `parent` links (BFS via a queue + visited set — since each commit has at most one parent here, this is effectively a linear walk). Commit objects are parsed by splitting header/message on the first `\n\n` and reading `tree`/`parent`/`author`/`committer` header lines.

### Status (`StatusCommand`)
Compares three sets: index (staged), working directory files (walked from `Repository.getWorkingDir()`, excluding `.git`), and hashes files on the fly (re-deriving the blob SHA-1 the same way `ObjectStore.storeBlob` would) to detect staged-but-modified files vs. untracked files.
