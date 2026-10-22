# Carpet Extra: Minecraft 26.3 Fabric port

This is a modified version of [gnembon/carpet-extra](https://github.com/gnembon/carpet-extra), an extension of Fabric Carpet. Original authors include gnembon, DeadlyMC, ImUrX, 2No2Name, and the upstream contributors. Existing authorship and license notices are retained. This fork is not an official upstream release.

Modified by pohsblzisgwc on 2026-10-22, starting from commit `c32b347f7dbad85e6c9fa0c4bd61f594dd1ffe4a`. The changes port the mod from Minecraft 26.2 to 26.3 on Fabric, adapt block-transformer, fertilization and spawning APIs and Mixin targets, update build dependencies, add static Mixin validation, and prepare source and binary distribution. See [the migration report](docs/porting-26.3.md) for details and validation limits.

Carpet Extra and these modifications are distributed under the GNU Lesser General Public License version 3. The unchanged LGPLv3 text is in `LICENSE`; the incorporated GNU GPL version 3 text is in `COPYING`. No additional restrictions are imposed. The Gradle Wrapper retains its separate license in `gradle/wrapper/gradle-wrapper.jar`, under `META-INF/LICENSE`.

## Corresponding source and build instructions

The complete preferred source, resources, build scripts, Gradle Wrapper, and validation tooling for the release are available at:

- Repository and history: https://github.com/pohsblzisgwc/carpet-extra
- Release tag: https://github.com/pohsblzisgwc/carpet-extra/tree/26.3
- Full source archive, alongside the JAR: https://github.com/pohsblzisgwc/carpet-extra/releases/tag/26.3

The `carpet-extra-26.3-source.zip` asset contains the complete tagged source tree. The `carpet-extra-26.3-sources.jar` is an additional IDE-oriented source artifact, not a replacement for the full source archive.

To rebuild, install JDK 25, extract the full source archive, open its root directory, and run:

```sh
chmod +x gradlew
./gradlew clean build
```

The build downloads its declared dependencies and writes the mod and source JARs to `build/libs`. It performs compilation and static checks without starting Minecraft. Runtime GameTests can be requested separately with `./gradlew runGameTest`.

To install or replace this mod, place the mod JAR in the instance's `mods` directory alongside compatible, separately obtained Fabric Loader and Carpet installations for Minecraft 26.3. Minecraft, Fabric Loader, Carpet, and their libraries are not bundled in the mod JAR and retain their own licenses. There are no signing keys or activation steps required to build or install a modified version of this mod.
