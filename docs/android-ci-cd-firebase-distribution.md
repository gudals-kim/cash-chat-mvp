# Android CI/CD — GitHub Actions → Firebase App Distribution

## 개요

`apps/frontend`(Android)에 변경이 `dev` 브랜치에 push될 때마다 자동으로 APK를 빌드하고,
Firebase App Distribution으로 업로드해서 테스터들이 이메일로 알림을 받을 수 있도록 설정한다.

## 전체 흐름

```
dev 브랜치 push (apps/frontend/** 변경)
  → GitHub Actions 트리거
  → JDK 17 세팅 + Gradle 캐시
  → Keystore 복원 (GitHub Secret)
  → google-services.json 복원 (GitHub Secret)
  → ./gradlew assembleRelease
  → Firebase App Distribution 업로드
  → 테스터 이메일 알림 발송
```

---

## 사전 준비 (1회 작업)

### 1. Keystore 생성

```bash
keytool -genkey -v -keystore cashchat-release.keystore \
  -alias cashchat -keyalg RSA -keysize 2048 -validity 10000
```

> **주의**: 생성된 `.keystore` 파일은 절대 Git에 커밋하지 않는다.

base64로 인코딩해서 GitHub Secret에 저장:

```bash
# macOS/Linux
base64 -i cashchat-release.keystore

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("cashchat-release.keystore"))
```

### 2. `key.properties` 생성 (로컬 전용, .gitignore에 추가됨)

`apps/frontend/key.properties`:
```properties
storePassword=<STORE_PASSWORD>
keyPassword=<KEY_PASSWORD>
keyAlias=cashchat
storeFile=../cashchat-release.keystore
```

### 3. Firebase 프로젝트 설정

1. [Firebase Console](https://console.firebase.google.com/) → 프로젝트 생성
2. Android 앱 추가 → 패키지명: `com.nomadclub.cashchat`
3. `google-services.json` 다운로드 → `apps/frontend/app/` 에 배치
4. **App Distribution** → 테스터 그룹(`testers`) 생성 및 이메일 추가
5. **Service Account 생성**:
   - [Google Cloud Console](https://console.cloud.google.com/) → IAM → 서비스 계정
   - `Firebase App Distribution Admin` 역할 부여
   - JSON 키 다운로드

### 4. GitHub Secrets 등록

레포 → Settings → Secrets and variables → Actions:

| Secret 이름 | 값 |
|------------|-----|
| `KEYSTORE_BASE64` | keystore 파일 base64 인코딩 값 |
| `KEYSTORE_STORE_PASSWORD` | storePassword |
| `KEYSTORE_KEY_ALIAS` | `cashchat` |
| `KEYSTORE_KEY_PASSWORD` | keyPassword |
| `FIREBASE_APP_ID` | Firebase 앱 ID (`1:xxx:android:xxx`) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | 서비스 계정 JSON 전체 내용 |
| `GOOGLE_SERVICES_JSON` | `google-services.json` 전체 내용 |

---

## 변경된 파일 목록

| 파일 | 변경 내용 |
|------|-----------|
| `apps/frontend/gradle/libs.versions.toml` | Firebase 플러그인 버전 추가 |
| `apps/frontend/build.gradle.kts` | Firebase 플러그인 classpath 추가 |
| `apps/frontend/app/build.gradle.kts` | signing config + Firebase App Distribution 설정 |
| `apps/frontend/.gitignore` | keystore, key.properties, google-services.json 추가 |
| `.github/workflows/android-distribute.yml` | CI/CD 워크플로우 (신규) |

---

## 검증 방법

1. `dev` 브랜치에 `apps/frontend` 내 파일 변경 후 push
2. GitHub → Actions 탭에서 워크플로우 실행 확인
3. Firebase Console → App Distribution에서 빌드 업로드 확인
4. 테스터 이메일로 알림 수신 확인
5. 링크 클릭 후 APK 다운로드 및 설치 확인

---

## 주의사항

- `google-services.json`, `cashchat-release.keystore`, `key.properties`는 `.gitignore`에 등록 — 절대 커밋 금지
- Firebase 테스터 그룹명(`testers`)은 Firebase Console에서 만든 그룹명과 일치해야 함
- AGP 9.0.1 + Firebase App Distribution 플러그인 5.x 호환 — 빌드 오류 시 버전 조정 필요
