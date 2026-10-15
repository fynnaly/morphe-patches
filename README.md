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
> **[v1.0.0-dev.1](https://github.com/fynnaly/morphe-patches/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 20 Minutes Till Dawn&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 8.6 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Bypass PairIP signature & license check](#bypass-pairip-signature-license-check) | Neutralizes PairIP tamper gates (SignatureCheck, LicenseClient, VMRunner) so re-signed APKs no longer crash in Application.attachBaseContext. Required for every Minutes Till Dawn patch. |  |

</details>

<details open>
<summary>📦 XYZ app&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.0.0 | 1.0.2 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Example Patch](#example-patch) | Example patch to start with. |  |

</details>

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
