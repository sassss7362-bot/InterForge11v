# InterWorld (Spigot / Paper 1.18 і новіші, Java 17)
Кастомний генератор світу: материки й океани, річки, високі гори, печери
(зали, тунелі, лускаві/дріпстоунові, кришталеві), власні структури.

Збірка: mvn clean package -> target/InterWorld.jar -> plugins/

Команди (право intertime.world.admin):
  /iw create <назва> [seed]  - створити світ
  /iw tp <назва>             - телепорт
  /iw list                   - список світів
  /iw here                   - біом і висота ландшафту тут

Через bukkit.yml:
  worlds:
    anarchy:
      generator: InterWorld
