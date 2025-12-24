# Messenger Platform - Electron Desktop App

이 문서는 Messenger Platform을 Electron 데스크톱 앱으로 실행하고 빌드하는 방법을 설명합니다.

## 개발 환경 실행

### 1. 개발 서버 실행 (개발 중)

프론트엔드와 Electron을 동시에 실행:

```bash
npm run electron:dev
```

이 명령은:
- Next.js 개발 서버를 http://localhost:3000에서 실행
- Electron 앱이 개발 서버에 연결됨
- 코드 변경 시 자동으로 새로고침 (Hot Reload)

### 2. Electron만 실행 (Next.js 서버가 이미 실행 중일 때)

```bash
npm run electron
```

## 프로덕션 빌드

### Windows용 빌드
```bash
npm run electron:build:win
```

빌드 완료 후 `dist/` 폴더에 설치 파일이 생성됩니다:
- `Messenger Platform Setup x.x.x.exe` - NSIS 설치 프로그램

### macOS용 빌드
```bash
npm run electron:build:mac
```

### Linux용 빌드
```bash
npm run electron:build:linux
```

### 모든 플랫폼 빌드
```bash
npm run electron:build
```

## 백엔드 서버 설정

### 개발 환경
`.env.local` 파일에서 로컬 백엔드 주소 설정:
```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=http://localhost:8080/ws
```

### 프로덕션 환경
`.env.production` 파일에서 원격 백엔드 서버 주소 설정:
```env
NEXT_PUBLIC_API_URL=https://your-backend-server.com
NEXT_PUBLIC_WS_URL=wss://your-backend-server.com/ws
```

**중요:** 빌드하기 전에 `.env.production` 파일의 URL을 실제 백엔드 서버 주소로 변경하세요!

## 프로젝트 구조

```
frontend/
├── electron/
│   ├── main.js          # Electron 메인 프로세스
│   └── preload.js       # Preload 스크립트
├── src/                 # Next.js 앱 소스
├── public/              # 정적 파일 및 아이콘
├── out/                 # Next.js 빌드 출력 (static export)
├── dist/                # Electron 빌드 출력
├── .env.local           # 개발 환경 변수
├── .env.production      # 프로덕션 환경 변수
└── package.json
```

## 아이콘 설정

앱 아이콘을 변경하려면 `public/icon.png` 파일을 교체하세요.
- 권장 크기: 512x512 픽셀 이상
- 형식: PNG (투명 배경 가능)

## 빌드 설정 커스터마이징

`package.json`의 `build` 섹션에서 Electron Builder 설정을 수정할 수 있습니다:
- `appId`: 앱 ID
- `productName`: 앱 이름
- 플랫폼별 설정 (win, mac, linux)
- 파일 포함/제외 설정

## 문제 해결

### 포트 충돌
Next.js 개발 서버가 3000 포트를 사용합니다. 다른 앱이 3000 포트를 사용 중이면 충돌이 발생할 수 있습니다.

### 빌드 실패
1. `node_modules` 삭제 후 `npm install` 재실행
2. `out/` 폴더 삭제 후 다시 빌드

### WebSocket 연결 실패
- `.env.production` 파일의 WebSocket URL이 올바른지 확인
- HTTPS를 사용하는 경우 WebSocket도 WSS를 사용해야 함

## 배포

빌드된 설치 파일(`dist/` 폴더)을 사용자에게 배포하면 됩니다:
- Windows: `.exe` 파일
- macOS: `.dmg` 파일
- Linux: `.AppImage` 파일

사용자는 설치 파일을 실행하여 앱을 설치하고 사용할 수 있습니다.
