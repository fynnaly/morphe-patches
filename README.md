# 👋🧩 Fynnaly can Patch

Personal collection of Morphe patches.

## How to use these patches

1. [Add Fynnaly can Patch to Morphe
   Manager](https://morphe.software/add-source?github=fynnaly/morphe-patches)
2. Or, copy this link to Morphe Manager: Remote -> `https://github.com/fynnaly/morphe-patches`
3. Enable **Pre-release** in the source settings to receive `dev` builds before
   a stable release is cut to `main`.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->

<!-- Do not modify this section by hand. The patch list is generated when release.yml creates a new release.

     If you wish for the patches list to be collapsed, then remove the word 'EXPANDED' from the comment tag above.

     If you wish to manually keep this list updated then remove the PATCHES_START and PATCHES_END
     comment blocks entirely. -->

### A list of your patches will automatically be shown here after your first patches release is created.

&nbsp;

<!-- The patches end tag is intentionally placed here so the first release will clean up
     this readme of all developer instructions above. -->
<!-- PATCHES_END -->

## 🛠️ Development

### Building locally

- `./gradlew :patches:buildAndroid` → `patches/build/libs/patches-*.mpp`
- Load the `.mpp` into [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.
- The build resolves the `app.morphe.patches` plugin from GitHub Packages, so a
  GitHub PAT with `read:packages` is required. Store it in
  `~/.gradle/gradle.properties` as `gpr.user` and `gpr.key`, never in this repo.

### Branching

- `dev` — development and pre-releases. Every `feat:` / `fix:` commit produces a
  `v*.dev.n` pre-release via `release.yml`.
- `main` — stable releases only. Merge `dev` into `main` (no squash) once the
  patches are verified on-device.

### Conventions

- [Semantic commits](https://www.conventionalcommits.org/) only: `feat:`,
  `fix:`, `chore:`. Only `feat:` and `fix:` trigger a release.
- Never hand-edit generated files (`patches-list.json`, `patches-bundle.json`,
  `CHANGELOG.md`) — `release.yml` owns them.
- Never force-push a semantic-release commit; cut a new release instead.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation)
for patcher API details.

## 📜 License

Fynnaly can Patch is licensed under the [GNU General Public License v3.0](LICENSE)
