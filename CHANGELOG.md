# Changelog

## [0.3.1](https://github.com/leandroAmariles/quipux-playlist-backend/compare/v0.3.0...v0.3.1) (2026-11-27)


### Bug Fixes

* **HU-11:** discard invalid audit timestamps and verify non-blocking publication ([1d55c89](https://github.com/leandroAmariles/quipux-playlist-backend/commit/1d55c89047d7b7540b43f52060e29a623757dc30))

## [0.3.0](https://github.com/leandroAmariles/quipux-playlist-backend/compare/v0.2.1...v0.3.0) (2026-11-27)


### Features

* **HU-10:** cache Spotify genres in Redis ([bdc2dd1](https://github.com/leandroAmariles/quipux-playlist-backend/commit/bdc2dd1a7de8ff512f1232dadfccfefa7655884e))
* **HU-10:** cache Spotify genres in Redis ([9fa775e](https://github.com/leandroAmariles/quipux-playlist-backend/commit/9fa775e25b7edbe560c9a64ba9b702a87899ce32))
* **HU-11:** publish and audit playlist events in Kafka ([9de3e53](https://github.com/leandroAmariles/quipux-playlist-backend/commit/9de3e53ad83e3ceab84d591391d65ccd58fc5c25))
* **HU-11:** publish and audit playlist events in Kafka ([bc15e89](https://github.com/leandroAmariles/quipux-playlist-backend/commit/bc15e894ccef5a0641908898f0dba018fc3ba076))
* **HU-13:** recommend songs from a catalog indexed in pgvector ([311f7ac](https://github.com/leandroAmariles/quipux-playlist-backend/commit/311f7ac6b73aecc06c1a715c94df4a8b32808773))
* **HU-13:** recommendations over a song catalog with pgvector ([b1ec807](https://github.com/leandroAmariles/quipux-playlist-backend/commit/b1ec807d4c674cb32cbc6104d3e681a24228aece))


### Bug Fixes

* **HU-11:** generate event ids without blocking the event loop ([fb5e89f](https://github.com/leandroAmariles/quipux-playlist-backend/commit/fb5e89f4f21e147823d03f80d3d8489f520154f8))


### Documentation

* **HU-13:** record the verification with main merged ([6066a6c](https://github.com/leandroAmariles/quipux-playlist-backend/commit/6066a6cb74effeff5264c33defd956d010785878))
* **HU-13:** specify and document the catalog recommendations ([e9dd417](https://github.com/leandroAmariles/quipux-playlist-backend/commit/e9dd417edd14fd9166bb71f8437716b3e456547f))

## [0.2.1](https://github.com/leandroAmariles/quipux-playlist-backend/compare/v0.2.0...v0.2.1) (2026-11-27)


### Bug Fixes

* **HU-09:** open infra tag PR automatically after backend image publish ([e0ce847](https://github.com/leandroAmariles/quipux-playlist-backend/commit/e0ce847267640eb57e99b5ee51805ef05adfbc63))
* **HU-09:** open infra tag PR automatically after backend image publish ([dd8da0b](https://github.com/leandroAmariles/quipux-playlist-backend/commit/dd8da0b2c44b7705d9f834bfd5e2c23d5dc53888))


### Documentation

* **HU-09:** document health probes, update-infra and close HU-09 backlog ([764faf9](https://github.com/leandroAmariles/quipux-playlist-backend/commit/764faf9d07a116a5088b9ded9ede5746aee8eeaa))
* **HU-09:** document health probes, update-infra and close HU-09 backlog ([2fcadd1](https://github.com/leandroAmariles/quipux-playlist-backend/commit/2fcadd1ca684f67a287446f6400912d36b074e85))

## [0.2.0](https://github.com/leandroAmariles/quipux-playlist-backend/compare/v0.1.1...v0.2.0) (2026-11-27)


### Features

* **HU-12:** AI recommendations for a playlist with a default fallback ([f75df79](https://github.com/leandroAmariles/quipux-playlist-backend/commit/f75df79d1f6c647895abf86967cbb1803a9d2b33))


### Bug Fixes

* **HU-12:** do not start with a request quota below one ([413f739](https://github.com/leandroAmariles/quipux-playlist-backend/commit/413f7397b475d7939b392ae9124097dffee625d1))


### Documentation

* **HU-12:** record the Qodana fix in the AI log ([0bcd69e](https://github.com/leandroAmariles/quipux-playlist-backend/commit/0bcd69e4313609aa1d0534b701f138518b4bc9e2))

## [0.1.1](https://github.com/leandroAmariles/quipux-playlist-backend/compare/v0.1.0...v0.1.1) (2026-11-27)


### Documentation

* **HU-08:** mark HU-08 completed in backlog and update ADR-0001 ([3e3554e](https://github.com/leandroAmariles/quipux-playlist-backend/commit/3e3554e14bc8989b12296709dd16bb54ad44b616))

## 0.1.0 (2026-11-27)


### Features

* **HU-02:** add playlist domain model and rules ([7d5a2a8](https://github.com/leandroAmariles/quipux-playlist-backend/commit/7d5a2a83df89d5e675fc850eec053ceda6d90de6))
* **HU-02:** add playlist use cases and ports ([5adcd5f](https://github.com/leandroAmariles/quipux-playlist-backend/commit/5adcd5f11b5bed16d12b5fbb23135e7b2ede27c1))
* **HU-02:** expose playlist endpoints backed by R2DBC ([fd95aa6](https://github.com/leandroAmariles/quipux-playlist-backend/commit/fd95aa67726ad8a2b8410e30d0c48a869aa70496))
* **HU-02:** playlist management ([ee8c1e5](https://github.com/leandroAmariles/quipux-playlist-backend/commit/ee8c1e5bf62652e1d3918545798252e8c84ad35c))
* **HU-03:** authentication and authorization ([886ed2a](https://github.com/leandroAmariles/quipux-playlist-backend/commit/886ed2aceb972ecbbd9cba63779239b83a64e527))
* **HU-03:** implement authentication and authorization with JWT, user roles, and demo users ([4d29517](https://github.com/leandroAmariles/quipux-playlist-backend/commit/4d29517939b0b9b72e25b2d6d0ff7674f5bfe57f))
* **HU-04:** genre catalog with Spotify fallback and validation ([036dc83](https://github.com/leandroAmariles/quipux-playlist-backend/commit/036dc838d351f568e4a57fe734401a467dc000d9))
* **HU-04:** implement genre management with local and Spotify sources, including error handling and configuration ([bcc51f7](https://github.com/leandroAmariles/quipux-playlist-backend/commit/bcc51f75348008236cb577037520144bc2993cc6))
* **HU-06:** integrate springdoc for OpenAPI documentation and configure public access routes ([de7d6ce](https://github.com/leandroAmariles/quipux-playlist-backend/commit/de7d6ce3b4fea46805edebeada5becf463549ead))
* **HU-06:** Postman collection, OpenAPI documentation and README ([f5103c7](https://github.com/leandroAmariles/quipux-playlist-backend/commit/f5103c70ba00723bad3ea7c6b8c87b2fb7e0efa6))
* **HU-07:** add the API Docker image ([bb0985e](https://github.com/leandroAmariles/quipux-playlist-backend/commit/bb0985e63dd98963a4310be534f7f66a5328ba61))
* **HU-07:** API Docker image ([d3a75b3](https://github.com/leandroAmariles/quipux-playlist-backend/commit/d3a75b31b12a2ad7cdf4b8da7e52e356f63f8952))


### Documentation

* group the backlog into eleven stories ([3961bcc](https://github.com/leandroAmariles/quipux-playlist-backend/commit/3961bcccfd10f11790b27574f97d35f281270162))
* **HU-01:** add specification, plan, tasks and build instructions ([bbde303](https://github.com/leandroAmariles/quipux-playlist-backend/commit/bbde303082179d9473341ecd2035e079684e3440))
* **HU-01:** mark the delivery task as done ([5b6d6d5](https://github.com/leandroAmariles/quipux-playlist-backend/commit/5b6d6d5e2a08edd4add7934e348a3c7ab012b311))
* **HU-02:** add playlist management specification and plan ([97ef889](https://github.com/leandroAmariles/quipux-playlist-backend/commit/97ef8892091109b52ad3fe2527c5178916fdcf94))
* **HU-02:** document the API and add HTTP test requests ([07336fa](https://github.com/leandroAmariles/quipux-playlist-backend/commit/07336fa5e4cf99519bcacd3f50af5e78207ab65b))
* **HU-02:** mark the delivery task as done ([24b6be8](https://github.com/leandroAmariles/quipux-playlist-backend/commit/24b6be8dfaa94a7b3a1bc07d490c16fe06e70592))
* **HU-02:** record code review notes for audit ([24cca03](https://github.com/leandroAmariles/quipux-playlist-backend/commit/24cca034cd3b1fe06f8c991d2c72e81e33bf5a24))
* **HU-05:** mark the web app as delivered ([dba264e](https://github.com/leandroAmariles/quipux-playlist-backend/commit/dba264eac086ffd5cc3e4a96588f6585df95d879))
* **HU-05:** mark the web app as delivered ([21911e2](https://github.com/leandroAmariles/quipux-playlist-backend/commit/21911e2f3fbd60afeb889732d5511e91a884387b))
* **HU-08:** document CI, releases, images and manual settings ([cae50ca](https://github.com/leandroAmariles/quipux-playlist-backend/commit/cae50cafe6a5b05f26a77e2f930ce36495fe878a))
