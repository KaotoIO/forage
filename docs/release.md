# Release Forage

Releases are fully automated by the `release` GitHub workflow. It runs
`maven-release-plugin` (`release:prepare` + `release:perform`), publishes to Maven Central,
updates the website version in `website/mkdocs.yml` on `main`, and creates the GitHub
release with JReleaser.

The workflow takes two inputs:

- `releaseVersion` – the version to release (e.g. `1.6.0`)
- `nextDevelopmentVersion` – the next development version, **without** the `-SNAPSHOT`
  suffix (e.g. `1.6.1`)

Run it against the branch you want to release (see the branching strategy in `CLAUDE.md`):

```shell
# Current LTS line (main)
gh workflow run release --ref main -f releaseVersion=1.6.0 -f nextDevelopmentVersion=1.6.1

# Previous LTS maintenance line
gh workflow run release --ref camel-4.18.x -f releaseVersion=1.4.1 -f nextDevelopmentVersion=1.4.2

# Latest (non-LTS) line, when active
gh workflow run release --ref camel-latest -f releaseVersion=1.5.1 -f nextDevelopmentVersion=1.5.2
```

Release `main` before the maintenance branches: every branch pushes its
`website/mkdocs.yml` version bump to `main`.

It may take from a few minutes to as much as 1 hour for the Maven Central publication to
happen. You can verify the publication status on the
[Publishing Page](https://central.sonatype.com/publishing).

After the workflow completes, check that:

- the `v<releaseVersion>` tag and the GitHub release exist,
- `website/mkdocs.yml` on `main` shows the new version,
- the artifacts are visible on Maven Central.

The Camel version keys in `website/mkdocs.yml` (`camel_lts_version`,
`camel_previous_lts_version`, `camel_latest_version`) and the versions in `README.md` are
**not** updated by the workflow and must be bumped manually when the Camel line changes.
