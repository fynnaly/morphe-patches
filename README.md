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
> **[v1.1.0-dev.9](https://github.com/fynnaly/morphe-patches/releases/tag/v1.1.0-dev.9)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;3 patches total
<details open>
<summary>📦 20 Minutes Till Dawn&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 8.6 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Bypass PairIP signature, license & VM gate](#bypass-pairip-signature-license-vm-gate) | Neutralises the PairIP signature and license gates, restores the virtualised lifecycle methods with real Unity delegating bodies, and skips the startup VM program. Required for every Minutes Till Dawn patch. |  |
| [Skip rewarded ads](#skip-rewarded-ads) | Grants the rewarded-ad reward without playing the video, so every "watch an ad to unlock" gate (heroes, guns, coupons, daily gift) resolves instantly. |  |

</details>

<details open>
<summary>📦 Spaceflight Simulator&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.6.00.22 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock expansions](#unlock-expansions) | Makes the store show the expansion packs and bundles as owned, without touching the game's ownership state: the card's own owned-or-price decision in libil2cpp.so is forced to the owned branch (arm64-v8a and armeabi-v7a). No receipt is faked, so Play Billing itself is untouched. |  |

</details>

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
