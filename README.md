# Inspector/Worker App — Android-порт бота на MVVM + Clean Architecture

## 1. Структура проекта

```
app/src/main/java/com/imedia/inspector/
├── MainActivity.kt
├── di/
│   └── AppModule.kt                 // Retrofit/OkHttp, репозиторий, ViewModelFactory
├── data/
│   ├── remote/
│   │   ├── Bitrix24ApiService.kt    // Retrofit-интерфейс (аналог callB24Method)
│   │   └── dto/BitrixDtos.kt        // B24EnvelopeDto, ContactDto, ListElementDto, FileDataDto
│   └── repository/
│       └── BitrixRepository.kt      // интерфейс + BitrixRepositoryImpl
├── domain/
│   └── model/Models.kt              // Contact, AddressItem, UserRole, AddressStatus,
│                                     // BreakageReason, AppScreenState (стейт-машина)
├── presentation/
│   ├── viewmodel/MainViewModel.kt   // вся бизнес-логика бота
│   ├── navigation/RootScreen.kt     // роутер по AppScreenState
│   ├── screens/
│   │   ├── RegistrationScreen.kt    // NeedRegistrationScreen / PendingRegistrationScreen
│   │   ├── InspectorScreen.kt       // роль 225 + BottomSheet выбора поломки
│   │   └── WorkerScreen.kt          // роль 227
│   └── components/
│       └── CameraCaptureButton.kt   // TakePicture() + превью
└── util/
    ├── BitrixParamsBuilder.kt       // "расплющивание" Map/List в form-urlencoded ключи
    └── FileNameUtils.kt             // имя файла + Base64 (аналог PHP-шаблона)
```

## 2. Карта соответствия PHP → Kotlin

| PHP                                    | Kotlin                                                        |
|-----------------------------------------|----------------------------------------------------------------|
| `callB24Method($method, $params)`       | `Bitrix24ApiService` + `BitrixParamsBuilder.build()`            |
| `getContact($maxid)`                    | `BitrixRepository.getContact()`                                 |
| `setState($conId, $state)`              | `BitrixRepository.setState()`                                   |
| `getlist($route, $skip)`                | `BitrixRepository.getAddressList()`                             |
| `getReplist($route, $skip)`             | `BitrixRepository.getRepairList()`                              |
| `updList($list)`                        | `BitrixRepository.updateAddress()` + `AddressUpdatePatch`        |
| `addLead()` / `getLead()`               | `BitrixRepository.addLead()` / `getLeadId()`                    |
| `$bxContact['position'] == '225'`       | `UserRole.INSPECTOR` ветка `AppScreenState.InspectorFlow`        |
| `$bxContact['position'] == '227'`       | `UserRole.WORKER` ветка `AppScreenState.WorkerFlow`               |
| числовые `state` (2,6,9,12,16,19...)    | `InspectorMode` / `WorkerMode` sealed-состояния                  |
| Reply-кнопки бота (`createKeyboard`)    | Compose `Button`/`OutlinedButton`/`ModalBottomSheet`              |
| Приём `attachments[].type == 'image'`   | `CameraCaptureButton` (`ActivityResultContracts.TakePicture()`) |
| Имя файла + `base64_encode()`           | `FileNameUtils.buildFileName()` / `fileToBase64()`                |

## 3. Примечания по бизнес-логике

- **Инспектор (225):** запрашивает адрес (`PROPERTY_109 = N`), либо отправляет фото рекламы
  (`Y`), либо помечает адрес пропущенным. Пропуск открывает `ModalBottomSheet` с двумя
  сценариями: "Лифт не работает" → `S`, либо "Стенд сломан" → выбор конкретной причины
  (замена стекла / планки+стекла / стенда) → `R` + `PROPERTY_119`.
- **Ремонтник (227):** видит адреса со статусом `R` (или `RS` — пропущенные), видит причину
  поломки (`PROPERTY_119`) на карточке адреса, загружает фото выполненного ремонта → `R1`.
- **Регистрация:** если `getContact()` не вернул `id` — проверяем `getLead()`; если лида
  нет — экран регистрации с вызовом `addLead()`; если лид уже создан — экран ожидания.
- Загруженный оригинальным ботом файл получали как готовый `image_url` от MAX; в Android
  вместо этого используется системная камера, снимок кодируется в Base64 и передаётся в
  `PROPERTY_115.fileData = [имя_файла, base64]` — те же самые ключи, что ждёт Bitrix24 REST.

## 4. Сборка APK

Проект теперь содержит полную обвязку Gradle (`settings.gradle.kts`, корневой
`build.gradle.kts`, `gradle.properties`, `gradlew`/`gradlew.bat`,
`gradle/wrapper/gradle-wrapper.properties`).

**Важно:** бинарный файл `gradle/wrapper/gradle-wrapper.jar` в архиве отсутствует
(его нельзя сгенерировать без доступа в интернет). Ничего делать вручную не нужно —
при первом открытии проекта в Android Studio она сама предложит "Gradle Sync" и
докачает нужный wrapper-jar автоматически. Если хотите собрать через терминал без
Android Studio, один раз выполните (при установленном Gradle через `brew`/`sdkman`):

```bash
cd BitrixInspectorApp/BitrixApp
gradle wrapper --gradle-version 8.7
```

это создаст недостающий `gradle-wrapper.jar`, после чего можно пользоваться `./gradlew`.

### Через Android Studio
1. `File → Open` → выбрать папку `BitrixApp`.
2. Дождаться Gradle Sync (первый раз — докачает Gradle 8.7 и AGP 8.4.2).
3. `Build → Build Bundle(s)/APK(s) → Build APK(s)`.
4. Файл появится в `app/build/outputs/apk/debug/app-debug.apk`.
5. Для запуска на телефоне: включить USB-отладку и нажать зелёную кнопку **Run ▶**.

### Через терминал
```bash
cd BitrixApp
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 5. Что нужно доработать перед продакшеном

- Подключить `SnackbarHostState` вместо временного `Text(message)` в `RootScreen`.
- Обработать разрешение `CAMERA` через `rememberLauncherForActivityResult(RequestPermission())`
  перед первым вызовом `CameraCaptureButton`.
- Вынести `deviceUserId`/`displayName` в экран онбординга (сейчас — заглушка на `ANDROID_ID`).
- Добавить Hilt, если проект будет расти (текущий `AppModule` — простой service locator).
