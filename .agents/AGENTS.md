# Rules to Prevent Accidental Destruction

To prevent accidental data loss, code deletion, or workspace state corruption, the agent must adhere to the following rules:

1. **Verifying Destructive Commands**: Never run potentially destructive commands (e.g. `rm`, `git clean`, `git reset --hard`, `git checkout .`, `git branch -D`) without verifying the target files/directories and confirming with the user if they were not explicitly requested.
2. **Safe File Operations**: Avoid using `write_to_file` with `Overwrite = true` on existing source code files. Instead, make surgical modifications using `replace_file_content` or `multi_replace_file_content` to preserve neighboring implementations, comments, and docstrings.
3. **Data Safety**: Do not clear local application databases (e.g. SQLite, Room tables) or delete user shared preferences in the runtime environment unless specifically tasked with resetting the application state.
4. **Git Preservation**: Avoid force-pushing (`git push --force`) or performing actions that discard uncommitted local history.
