# 공시핑 — 아파트 공시가격 알림 (Android)

## 📲 APK 다운로드

현재 APK 배포를 중단했습니다.

찜한 아파트의 **새 공시가격 발표**를 푸시로 알려주고,
10년 가격 흐름과 주변 단지 ㎡당 공시가격 순위를 보여주는 앱.

> 1.5: 단지 전체 세대를 대량으로 조회하던 '단지 안 순위'·'엑셀 내보내기'와 '동네 실거래 알림'을 뺐습니다. 공시가격 정보만 봅니다.


## 1. 인증키 — 필요 없음

공시가격·단지 검색·동/호 선택·주변 비교는 국토교통부 **부동산공시가격 알리미(realtyprice.kr)** 의
공개 조회 경로를 그대로 써서 **인증키 없이** 동작합니다.

## 광고 (AdMob)

- 홈·아파트 화면 맨 아래 **적응형 하단 배너** 1개만 (전면 광고 없음).
- AdMob 앱 ID · 배너 광고 단위 ID 는 `app/build.gradle` 맨 위 `ADMOB` 블록에 들어 있습니다.
- 유럽 등 동의가 필요한 지역은 Google UMP 동의 창을 먼저 띄우고, 설정에 `광고 개인정보 설정`이 생깁니다.
- **내 폰에서 내 광고를 누르지 마세요** (AdMob 계정 정지 사유).
- 새 광고 단위는 실제 광고가 나오기까지 몇 시간~하루 걸릴 수 있어요.
- Play 스토어에 올릴 때는 `app-ads.txt` 등록, 앱 콘텐츠의 "광고 포함" 체크가 필요합니다.

## 2. 빌드

1. Android Studio → Open → `C:\Ai\GongsiPing`
2. Gradle Sync 가 끝나면 ▶ Run (폰 USB 연결) 또는 Build → Build APK(s)
3. 처음 실행 시 알림 권한을 허용하세요.

환경: AGP 8.10.1 · Gradle 8.14.3 · Kotlin 2.1.21 · Jetpack Compose · minSdk 26 / targetSdk 36

## 3. 동작 방식

- **서버 없음.** WorkManager 가 12시간마다(네트워크 연결 시) 폰에서 직접 확인.
- 공시가격: 공시가격 알리미의 공시 일자가 바뀌면 내 세대 기록을 다시 받아 새 연도가 생겼을 때 → "공시가격 나왔어요 · 작년보다 +4.2%"
- 주변 비교: 같은 법정동에서 지번이 가까운 단지 5/8/12곳의 ㎡당 공시가격(가운데 동 저·중·고층 3세대 표본의 중앙값)으로 순위. 7일마다 자동 갱신, 새로고침 가능.
- 데이터는 폰 내부 파일(`apts.json`)에만 저장.

## 4. 파일 구조

```
app/src/main/java/kr/home/gongsiping/
  MainActivity.kt        화면 전환, 알림 권한, 알림 탭 → 상세
  data/Api.kt            공시가격 알리미(키 없음)
  data/Repo.kt           추가·추이·주변비교·백그라운드 확인 로직
  data/Store.kt          찜 목록 저장(JSON) + 설정
  data/Fmt.kt            "5억 2,300만" 같은 숫자 표기
  data/Demo.kt           예시 데이터
  work/CheckWorker.kt    12시간 주기 확인 + 푸시
  ads/Ads.kt             AdMob 하단 배너, UMP 동의
  ui/Theme.kt            색 5개(종이·먹·회색·라임) + 글자 크기
  ui/Components.kt       형광펜 강조, 상승 태그, 꺾은선, 막대
  ui/HomeScreen.kt / SearchScreen.kt / DetailScreen.kt / SettingsScreen.kt
```

## 5. 디자인 원칙

오프화이트 종이 배경 + 먹색 글자 + 라임 형광펜 하나. 그라데이션·그림자·이모지·둥근 카드 없음.
숫자는 크게, 나머지는 작게. 왼쪽 정렬, 얇은 선으로만 구분. 다크모드 지원.

**Pretendard 글꼴(선택):** `Pretendard-Regular/Bold/ExtraBold/Black.otf` 를
`app/src/main/res/font/` 에 소문자 이름(`pretendard_regular.otf` 등)으로 넣고,
`ui/Theme.kt` 의 `FontFamily.Default` 를 `FontFamily(Font(R.font.pretendard_regular), ...)` 로 바꾸면 됩니다.

## 개인정보처리방침

[PRIVACY.md](PRIVACY.md)

## 6. 알아둘 점

- 공동주택 공시가격은 1년에 한 번(보통 3월 열람안 → 4월 말 확정) 발표돼요.
- 단지 이름은 공시가격 알리미에 등록된 이름 기준이에요. 안 나오면 짧게 검색해보세요 (예: '후곡마을').
- 공시가격 알리미는 공식 API가 아니라 공개 웹 조회 경로라, 사이트 구조가 바뀌면 조정이 필요할 수 있어요.
- 오피스텔·단독주택은 공동주택 공시가격 대상이 아니라 나오지 않아요.
