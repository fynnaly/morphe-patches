# 👋🧩 Fynnaly Patches

Morphe patch collection. Current focus: **20 Minutes Till Dawn** (Flanne) 8.6.

## ❓ About

Patches for games I actually play. First target is *20 Minutes Till Dawn*
(`com.Flanne.MinutesTillDawn.roguelike.shooting.fr.gp`) 8.6 — PairIP bypass as
the re-sign prerequisite, followed by per-category unlocks and a rewarded-ad
skip. More games will be added as needed.

| App | Version | Status |
| --- | --- | --- |
| 20 Minutes Till Dawn | 8.6 (versionCode 1093, APKPure XAPK arm64_v8a) | In development on `dev` |

## How to use these patches

Add this source in Morphe Manager: `https://morphe.software/add-source?github=fynnaly/morphe-patches`

Enable **Pre-release** in the source settings to receive `dev` builds before a stable release is cut to `main`.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->

<!-- Do not modify this section by hand. The patch list is generated when release.yml creates a new release.
     
     If you wish for the patches list to be collapsed, then remove the word 'EXPANDED' from the comment tag above.

     If you wish to manually keep this list updated then remove the PATCHES_START and PATCHES_END 
     comment blocks entirely. -->

#### A list of your patches will automatically be shown here after your first patches release is created.

&nbsp;

## 🚀 Development

Branching:

- `dev` — preview and experimental work. Every `feat:` / `fix:` commit here
  produces a pre-release (`v1.x.y-dev.n`) via `release.yml`.
- `main` — stable releases only. Merge `dev` into `main` (no squash) when a
  set of patches is verified on-device.

Local build:

- Run `./gradlew :patches:buildAndroid` (or `generatePatchesList` for the
  patch index). The built bundle lands in `patches/build/libs/patches-*.mpp`.
- Apply the `.mpp` with
  [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop) like any other
  patch bundle.

Conventions:

- Use [semantic commits](https://www.conventionalcommits.org/): `feat:`,
  `fix:`, `chore:`. Only `feat:` / `fix:` trigger a release.
- Never hand-edit generated files (`patches-list.json`,
  `patches-bundle.json`, `CHANGELOG.md`) — `release.yml` owns them.
- Never force-push a semantic-release commit; cut a new release instead.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation)
for patcher API details.


<!-- The patches end tag is intentionally placed here so the first release will clean up 
     this readme of all developer instructions above. -->
<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew :patches:buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## Roadmap

- [x] 20MTD 8.6 PairIP bypass (re-sign prerequisite)
- [ ] 20MTD unlock heroes (separate patch)
- [ ] 20MTD unlock guns (separate patch)
- [ ] 20MTD unlock skins (separate patch)
- [ ] 20MTD skip rewarded ads

## 📜 License

Fynnaly Patches are licensed under the [GNU General Public License v3.0](LICENSE)
