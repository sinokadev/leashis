# Leashis

플레이어에게 목줄을 연결하는 **서버 전용** 플러그인/모드입니다.
Bukkit(CraftBukkit)·Spigot·Paper, Fabric, NeoForge가 같은 연결 규칙과 당김 엔진을 사용합니다.
클라이언트에 모드나 리소스팩을 설치할 필요가 없습니다. 바닐라 투명 아기 거북이가 줄을 표시하며
새 아이템·엔티티 타입·필수 커스텀 네트워크 채널을 등록하지 않습니다.

## 지원 범위

| 배포 종류 | Minecraft 범위 | 서버 설치 위치 | 추가 의존성 |
| --- | --- | --- | --- |
| Bukkit 공통 플러그인 | 1.20부터 26.3까지 | `plugins/` | 없음 |
| Fabric | 1.20부터 26.3까지, 버전별 JAR | `mods/` | Fabric Loader, 해당 버전 Fabric API |
| NeoForge | 1.20.2부터 26.3까지, 버전별 JAR | `mods/` | 해당 버전 NeoForge |

정확한 패치 버전과 고정된 빌드 의존성은 [versions.json](versions.json)에 있습니다.
NeoForge 1.20.0은 존재하지 않으며 1.20.1의 구형 Forge 호환 NeoForge는 이 배포 대상에서 제외합니다.
일부 Minecraft 버전에는 NeoForge 베타만 존재하므로 버전표의 로더 버전도 확인하세요.
빌드 검증과 실제 실행 검증은 [검증 기록](docs/VALIDATION.md)에 구분해 기록합니다.

## 설치

- Bukkit·Spigot·Paper: `leashis-bukkit-2.0.0.jar`를 `plugins/`에 넣습니다.
- Fabric: 서버 버전과 일치하는 `leashis-fabric-<MC>-2.0.0.jar`와 Fabric API를 `mods/`에 넣습니다.
- NeoForge: 서버 버전과 일치하는 `leashis-neoforge-<MC>-2.0.0.jar`를 `mods/`에 넣습니다.

한 서버에는 해당 플랫폼용 Leashis JAR 하나를 설치하세요.
서버 런타임은 1.20~1.20.4에 Java 17 이상, 1.20.5~1.21.11에 Java 21 이상,
26.x에 Java 25 이상이 필요합니다. 서버 배포판이 요구하는 Java 버전도 따르세요.

## 사용

1. 목줄을 들고 다른 플레이어를 우클릭하면 연결됩니다. 양손 상호작용을 지원합니다.
2. 연결 후 1초 이상 지나 같은 대상을 다시 우클릭하면 해제됩니다. 빈손으로도 가능합니다.
3. 목줄을 들고 울타리를 우클릭하면 울타리 매듭으로 연결할 수 있습니다.

기본값으로 4블록부터 보유자 방향으로 당기고 10블록을 초과하면 줄이 끊어집니다.
생존 모드는 연결 성공 시 목줄 하나를 소비하고 해제 시 대상 근처에 한 번만 반환합니다.
크리에이티브로 시작한 연결은 소비·반환하지 않습니다. 이후 게임 모드를 바꿔도 이 규칙은 유지됩니다.
여러 대상을 동시에 연결할 수 있으며 이미 연결된 대상을 빼앗을 수 없습니다.

묶인 플레이어의 울타리 매듭 우클릭/직접 공격과 차원 이동(포털·차원을 바꾸는 명령)을 차단합니다.
사망·로그아웃·탑승·관전 모드·기능 비활성화·보유자 차원 변경 시 정리합니다.
정상 종료 시 소비한 목줄을 반환하며 재시작 후 연결은 유지하지 않습니다.
프록시는 월드에 저장하지 않습니다. Bukkit은 로드된 청크의 이전 프록시 표시도 정리합니다.

## 설정

| 플랫폼 | 설정 파일 | 반영 방법 |
| --- | --- | --- |
| Bukkit 계열 | `plugins/Leashis/config.yml` | `/leashis reload` (기본 OP) 또는 재시작 |
| Fabric | `config/leashis-server.properties` | 재시작 |
| NeoForge | `config/leashis-server.toml` | 로더 설정 재로딩 또는 재시작 |

NeoForge의 SERVER 설정을 사용하는 구버전에서는 월드의 `serverconfig/` 설정이 우선할 수 있습니다.
26.3의 LOCAL 설정은 서버 `config/`에서 읽습니다.

네 플랫폼의 설정 의미와 기본값은 같습니다.

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `enabled` | `true` | 기능 활성화 |
| `distanceMin` | `4` | 당김 시작 거리 (0~1024) |
| `distanceMax` | `10` | 끊김 거리 (1~1024) |
| `allowLeashedRemoveFenceKnot` | `false` | 묶인 플레이어가 매듭을 제거할 수 있는지 |

최소 거리가 최대 거리 이상이면 실제 당김 시작 거리를 최대 거리 - 1로 제한합니다.
Fabric의 `.properties` 파일은 `enabled=true`처럼 `키=값` 형식입니다.

## 빌드

Gradle 실행용 JDK 21 이상(전체 버전 빌드는 JDK 25 권장)과 Python 3이 필요합니다.
컴파일용 Java 17/21/25는 Gradle 도구 체인이 선택하며 필요한 경우 자동 다운로드합니다.

```sh
# 기본: Bukkit + Fabric 1.21.1 + NeoForge 1.21.1
./gradlew build

# 특정 Minecraft/플랫폼
./gradlew -Pmc=26.3 -Pplatforms=fabric,neoforge build
./gradlew -Pplatforms=bukkit :bukkit:build
./gradlew -Pmc=1.20 -Pplatforms=fabric :fabric:build

# 모든 지원 버전을 빌드하고 검증 결과를 기록
python3 tools/build_matrix.py --loader fabric
python3 tools/build_matrix.py --loader neoforge
python3 tools/package_release.py
```

JAR은 `bukkit/build/libs/`, `fabric/build/<MC>/libs/`, `neoforge/build/<MC>/libs/`에 생성됩니다.
배포 묶음은 `dist/`에 생성되며 플랫폼별 JAR·체크섬·버전표를 포함합니다.
배포 도구는 실패한 빌드나 누락된 JAR이 있으면 중단합니다.

## 검증

```sh
./gradlew -Pplatforms=core :core:check
./gradlew -Pplatforms=neoforge :neoforge:runGameTestServer

# 격리된 개발 서버 기동 검사 (개발용 EULA 파일과 localhost 설정 생성)
python3 tools/smoke_server.py --loader fabric --mc 26.3
python3 tools/smoke_server.py --loader neoforge --mc 1.20.2

# 두 바닐라 프로토콜 클라이언트를 이용한 통합 검사
npm ci --prefix tests/integration
python3 tools/smoke_server.py --loader fabric --mc 1.20.1 \
  --client-script tests/integration/regression.cjs
```

통합 검사는 소비·중복 클릭·줄 표시 패킷·반환·크리에이티브·당김·거리 끊김·울타리 연결·매듭 보호·차원 이동 차단·로그아웃 정리를 검사합니다.
프로토콜 검사는 실제 화면의 줄 모양을 확인하지 않으므로 두 실제 클라이언트의 시각 확인 항목은 검증 기록에 별도로 남깁니다.
테스트 코드와 테스트 월드는 배포 JAR에 포함하지 않습니다.

## 구조

- `core/`: 플랫폼에 의존하지 않는 상태 관리, 아이템 반환 소유권, 당김 계산.
- `bukkit/`: Bukkit 1.20 API로 작성한 공통 플러그인.
- `mod-common/`: Fabric/NeoForge가 공유하는 Minecraft 어댑터와 프록시.
- `fabric/`: Fabric 이벤트, 서버 설정, 차원 이동 Mixin.
- `neoforge/`: NeoForge 이벤트/설정, 구형 NeoGradle와 신형 ModDevGradle 빌드.
- `tools/generate_mod_sources.py`: 버전별 내부 API 차이를 컴파일 시 반영.
- `versions.json`: 지원 버전과 의존성의 단일 목록. CI가 같은 목록으로 빌드합니다.

## 레퍼런스

[pog5/mc-leashplayers](https://github.com/pog5/mc-leashplayers)의 바닐라 프록시 접근과 멀티플랫폼 구조를 참고했습니다.
참고 프로젝트의 MIT 고지는 `licenses/mc-leashplayers-MIT.txt`에 보존했습니다.
기존 NeoForge MDK의 `TEMPLATE_LICENSE.txt`도 보존했습니다.

## Development acknowledgment

Most of the functionality in this project was implemented by ChatGPT.
