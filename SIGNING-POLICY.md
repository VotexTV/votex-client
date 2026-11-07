# Code signing policy

Status: proposed policy for a SignPath Foundation application. The project has not been accepted, no signing identity is configured, and no 5.7 artifact has been signed.

After acceptance, the intended provider attribution is: Free code signing provided by [SignPath.io](https://about.signpath.io/), certificate by [SignPath Foundation](https://signpath.org/).

This policy describes the proposed signing scope, build process, and release authorization.

## Project

- Project: Votex Client — Windows launcher and Minecraft Fabric core
- License: MIT, see [LICENSE](LICENSE); no commercial dual licensing, no proprietary components
- Source repository: this repository; it is the only source of signed builds
- Maintainer and release approver: Lasse Wieting

## Team roles

- Authors (trusted to modify source code): Lasse Wieting
- Reviewers (review changes from non-committers): Lasse Wieting
- Approvers (approve every signing request): Lasse Wieting

Before signing is enabled, all team members must configure multi-factor authentication for both source repository and SignPath access.

## Artifacts that are signed

Only release artifacts of the Windows x64 launcher are signed:

1. `votex-launcher.exe` — the launcher binary embedded in the installer.
2. `uninstall.exe` — the generated NSIS uninstaller.
3. `Votex Client_<version>_x64-setup.exe` — the final NSIS installer produced by Tauri.

The current workflow prepares launcher and outer-installer signing only. Uninstaller export/sign/import is not implemented, and NSIS plugin DLL signing still requires confirmation. This is not a complete release-signing pipeline; no public release is authorized until those components and actual installation/uninstallation are accepted.

The Fabric core JAR (`votex-client-core-<version>.jar`), development builds, local test builds, and CI artifacts outside a tagged release remain unsigned.

## Build process

Signed artifacts are produced exclusively by the `signed-release` workflow in `.github/workflows/signed-release.yml` on `windows-latest` GitHub-hosted runners:

1. `pnpm install --frozen-lockfile` installs dependencies from the committed lockfile.
2. `pnpm test` must pass, including the hash-bound native cosmetic review gate.
3. The Java core is built and tested using the checked-in Gradle wrapper (`./gradlew.bat --no-daemon build compileGametestJava`); `scripts/stage-core.ps1` stages the core JAR.
4. `cargo test --locked` must pass before `pnpm desktop:build --no-bundle` builds the frontend and Rust release executable without NSIS packaging.
5. The launcher is uploaded as `unsigned-launcher` and submitted to SignPath using the `windows-launcher` artifact configuration. Signing requires manual approval.
6. The returned launcher must have valid timestamped Authenticode, the configured approved certificate thumbprint and the release version.
7. A separate runner stages that signed launcher, the built frontend and the core JAR. `pnpm exec tauri bundle --bundles nsis --no-binary-patching` packages without rebuilding or patching the signed launcher.
8. The resulting `unsigned-installer` artifact is submitted separately using the `windows-installer` configuration and requires another manual approval.
9. The installer must have valid timestamped Authenticode from the configured approved identity. Its extracted launcher must retain the approved signature, version and exact SHA-256 of `signed-launcher`.
10. Only a draft release with SHA-256 checksums is created. Publication requires actual installation, launcher, uninstallation and Smart App Control acceptance tests; a successful workflow alone is not release approval.

There is no manual upload path for signing requests and no signing from a developer workstation. Build inputs are the tagged commit and the committed lockfiles; no binary is patched after signing.

## Signing identity and timestamping

After Foundation acceptance and configuration, signatures are intended to use its publicly trusted code signing certificate and an RFC 3161 timestamp. The expected publisher identity must be taken from the certificate actually assigned to this project; none is assigned yet.

## Release authorization

- A signature is requested only for a commit tagged `v<version>` in this repository.
- The version must match `package.json`, `apps/launcher/src-tauri/tauri.conf.json`, and `apps/launcher/src-tauri/Cargo.toml`.
- Every signing request requires manual approval in SignPath by an approver protected by multi-factor authentication.
- `SIGNPATH_CERTIFICATE_THUMBPRINT` must match the certificate assigned to this project by SignPath; valid signatures from unrelated publishers are rejected.
- The workflow creates a draft only; actual installation, launcher, uninstallation and Smart App Control acceptance must be recorded before publication.
- Every release publishes SHA-256 checksums next to the installer.
- Releasing a fixed build uses a new version and a new signature; published artifacts are not replaced in place.

## Secrets and private material

The repository contains no code signing keys, certificates, tokens, or Minecraft binaries. The SignPath API token and organization ID are stored only as GitHub Actions secrets. Private keys never leave the signing service.

## Privacy policy

This program will not transfer any information to other networked systems unless specifically requested by the user or the person installing or operating it.

## Reporting a problem

If you believe a Votex Client binary was signed incorrectly or is being distributed by an unauthorized party, contact the maintainer through GitHub. A compromised signing identity leads to revoking the affected release and publishing a new version.
