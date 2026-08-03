# MiniGit

A simplified, educational reimplementation of core Git plumbing in Java. MiniGit reproduces Git's internal model - content-addressed objects, a staging area, commits, branches-as-pointers - at a small, readable scale. It is not a drop-in replacement for Git and doesn't aim to be: there's no compression, no packfiles, no merging, no networking. The goal is to make Git's actual mental model touchable in a few hundred lines of Java.

For a full breakdown of how each piece works and how they fit together, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Requirements

- A JDK (Java 11+ recommended). No other dependencies.
- No build tool — this project is compiled directly with `javac`.

## Setup

Clone or copy this repository, then from the project root:

```bash
javac -d out $(find src -name '*.java')
```

This compiles every `.java` file under `src/` into `out/`. Re-run this any time you change source files — there's no incremental build or watch mode.

## Running MiniGit

Every command is invoked the same way:

```bash
java -cp out com.minigit.MiniGit <command> [args...]
```

If you want a shorter invocation, you can put this in a shell alias:

```bash
alias minigit='java -cp out com.minigit.MiniGit'
```

(adjust the path to `out` if you're not running from the project root.)

## Commands

### `init`

Initializes a new repository in the current directory.

```bash
minigit init
```

Creates a `.git/` directory containing:
- `objects/` — the object store (blobs, trees, commits)
- `refs/heads/`, `refs/tags/` — branch and tag pointers
- `HEAD` — points at the current branch (`ref: refs/heads/main`)
- `config` — repository configuration (empty until you set values)
- `index` — the staging area (empty until you `add` something)

Running `init` again on an existing repository is safe — it prints `Reinitialized existing Git repository in ...` and does not touch any existing state.

### `config`

Sets a configuration value, stored in `.git/config`.

```bash
minigit config user.name "Jane Doe"
minigit config user.email "jane@example.com"
```

At minimum, set `user.name` and `user.email` before committing — `commit` reads these to build the commit's author line. If they're unset, commits fall back to a placeholder (`Unknown <unknown@example.com>`).

### `add`

Stages one or more files: reads each file's contents, stores it as a blob, and records it in the index.

```bash
minigit add file1.txt file2.txt
```

Re-running `add` on a file that's changed since it was last staged updates the staged content to match the file's current contents.

### `commit`

Takes a snapshot of everything currently in the index and records it as a new commit.

```bash
minigit commit -m "commit message"
```

The current branch (`main` by default) is advanced to point at the new commit. The index is **not** cleared after a commit — files stay staged/tracked across commits, same as real Git, until something else changes them.

If the index doesn't differ from the current commit's tree — nothing staged, or everything staged is identical to what's already committed — `commit` refuses to create a new commit and instead prints `nothing to commit, working tree clean`, matching real Git's behavior.

### `log`

Prints the commit history, walking backward from the current commit to the very first one.

```bash
minigit log
```

Output looks like:

```
commit <hash of newest commit>
<message>

commit <hash of an older commit>
<message>
```

If there are no commits yet, prints `No commits yet`.

### `status`

Shows the state of every file in the working directory relative to what's staged and what's committed:

```bash
minigit status
```

Output is grouped into up to three sections, in this order:

- **Changes to be committed** — differences between the index and the last commit: `new file:`, `modified:`, or `deleted:`.
- **Changes not staged for commit** — differences between the working directory and the index: `modified:` or `deleted:`.
- **Untracked files** — files on disk that are in neither the index nor the last commit.

If none of the three sections have anything to report, prints `nothing to commit, working tree clean`.

## Example walkthrough

```bash
mkdir my-project && cd my-project
java -cp /path/to/out com.minigit.MiniGit init

java -cp /path/to/out com.minigit.MiniGit config user.name "Jane Doe"
java -cp /path/to/out com.minigit.MiniGit config user.email "jane@example.com"

echo "hello" > a.txt
java -cp /path/to/out com.minigit.MiniGit add a.txt
java -cp /path/to/out com.minigit.MiniGit commit -m "initial commit"

java -cp /path/to/out com.minigit.MiniGit status
java -cp /path/to/out com.minigit.MiniGit log
```

## Known limitations

These are deliberate simplifications, not bugs:

- No nested directories — only flat files in the repo root are supported; tree entries store bare filenames.
- No `reset`, `diff`, `branch`, `checkout`, or `merge` — only the six commands above exist.
- `.git/index` and `.git/config` are simple line-based text formats (CSV-like and `key=value`), not Git's real binary/INI formats.
- No compression — objects are stored as raw `<type> <size>\0<content>` bytes, readable directly with tools like `xxd`.

## Tests

There is no automated test suite. Everything is verified by hand, by running the real CLI against a scratch directory and checking the output/`.git/` contents.
