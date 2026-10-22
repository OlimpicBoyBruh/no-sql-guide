# Словарь терминов MongoDB

Искать в документации MongoDB, в справке драйвера и в поиске нужно по английскому термину, поэтому он указан для каждой строки: «write concern» находит нужное, «гарантия записи» — не всегда.

| Термин | English | Определение | Где подробнее |
|---|---|---|---|
| **$bucket** | $bucket | Стадия агрегации, раскладывающая документы по диапазонам значений (не путать с паттерном bucket) | [05](05-aggregation.md#facet-bucket-и-оконные-функции) |
| **$expr** | $expr | Оператор, позволяющий использовать выражения агрегации внутри запроса; мост между операторами запроса и выражениями | [05](05-aggregation.md#операторы-запроса-и-выражения) |
| **$facet** | $facet | Стадия, запускающая несколько подконвейеров над одним входом | [05](05-aggregation.md#facet-bucket-и-оконные-функции) |
| **$group** | $group | Блокирующая стадия: группирует документы по ключу и считает аккумуляторы | [05](05-aggregation.md#unwind-и-group) |
| **$jsonSchema** | $jsonSchema | Оператор валидатора коллекции: описание допустимой формы документа, например `maxItems` для массива | [03](03-data-modeling.md#версия-схемы-и-валидация) |
| **$lookup** | $lookup | Стадия, соединяющая документы с другой коллекцией; без индекса по чужому полю выполняется вложенным циклом и дорого стоит | [05](05-aggregation.md#lookup-и-его-цена) |
| **$merge** | $merge | Стадия записи результата конвейера в коллекцию со вставкой или слиянием; позволяет строить идемпотентные витрины | [05](05-aggregation.md#запись-результата-merge-и-out) |
| **$out** | $out | Стадия, которая атомарно заменяет целевую коллекцию результатом конвейера | [05](05-aggregation.md#запись-результата-merge-и-out) |
| **$setWindowFields** | $setWindowFields | Стадия оконных функций (с 5.0): считает значение по окну соседних документов, не схлопывая их | [05](05-aggregation.md#facet-bucket-и-оконные-функции) |
| **$unwind** | $unwind | Стадия, разворачивающая массив в поток документов, по одному на элемент | [05](05-aggregation.md#unwind-и-group) |
| **2dsphere** | 2dsphere | Геопространственный индекс по сфере для запросов вроде «ближайшие точки» | [04](04-indexes.md#wildcard-text-и-2dsphere) |
| **afterClusterTime** | afterClusterTime | Параметр read concern причинной сессии: узел обслуживает чтение, только дойдя до этого времени | [07](07-read-write-concerns.md#причинная-согласованность-читать-свои-записи-с-secondary) |
| **allowDiskUse** | allowDiskUse | Разрешение блокирующей стадии сбрасывать данные на диск при превышении 100 МБ; в 8.0 включено по умолчанию | [05](05-aggregation.md#память-100-мб-и-allowdiskuse) |
| **At-least-once** | at-least-once | Гарантия доставки «хотя бы один раз»: событие может прийти повторно, поэтому обработчик должен быть идемпотентным | [10](10-change-streams-java.md#at-least-once-и-outbox) |
| **Atlas Search, Vector Search** | Atlas Search, Vector Search | Полнотекстовый и векторный поиск, работающие через процесс `mongot`; на образе `mongo:8.0` недоступны | [10](10-change-streams-java.md#atlas-search-и-vector-search) |
| **B-дерево** | B-Tree | Структура WiredTiger для коллекции и для каждого индекса: страницы на диске и в памяти | [02](02-wiredtiger.md#b-дерево-страницы-на-диске-и-в-памяти), [фон](../../docs/00-foundations/07-storage-engines.md) |
| **BSON** | BSON (Binary JSON) | Двоичный формат документа с явными типами и длинами в префиксе | [01](01-document-bson.md#bson-зачем-не-json) |
| **ChangeStreamHistoryLost** | ChangeStreamHistoryLost | Ошибка: токен возобновления старше окна oplog, продолжить поток нельзя | [10](10-change-streams-java.md#окно-oplog-и-changestreamhistorylost) |
| **Change stream** | change stream | Курсор поверх oplog, отдающий события после majority-коммита, с серверным фильтром | [10](10-change-streams-java.md#change-streams-поверх-oplog) |
| **Checkpoint** | checkpoint | Консистентный снимок данных, который WiredTiger записывает на диск каждые 60 секунд (`syncdelay`) | [02](02-wiredtiger.md#checkpoint) |
| **Collation** | collation | Правила сравнения строк для языка | [03](03-data-modeling.md#версия-схемы-и-валидация) |
| **COLLSCAN** | COLLSCAN | Стадия плана: полный просмотр коллекции без индекса | [04](04-indexes.md#индекс-и-explain-как-читать-план) |
| **Config servers** | config servers (CSRS) | Replica set, хранящий карту «диапазон ключа → шард» шардированного кластера | [09](09-sharding.md#устройство-кластера-mongos-config-servers-шарды) |
| **CSOT** | client-side operation timeout (CSOT) | Единый клиентский таймаут операции вместо россыпи мелких таймаутов драйвера | [07](07-read-write-concerns.md#java-где-задаются-настройки) |
| **Decimal128** | Decimal128 | Десятичный BSON-тип для денег и точных расчётов | [01](01-document-bson.md#числа-int-long-double-decimal128) |
| **explain()** | explain() | Команда показа плана запроса; режим `executionStats` добавляет `nReturned`, `totalKeysExamined`, `totalDocsExamined` | [04](04-indexes.md#индекс-и-explain-как-читать-план) |
| **fullDocument** | fullDocument | Поле события change stream; с `updateLookup` содержит текущую версию документа (или `null`) | [10](10-change-streams-java.md#событие-и-fulldocument) |
| **Hashed-ключ** | hashed shard key | Ключ шардирования по хешу поля: равномерные вставки, но диапазонные запросы идут на все шарды | [09](09-sharding.md#hashed-ranged-и-составной-ключ) |
| **Hidden-узел** | hidden member | Член replica set с `priority: 0`, невидимый приложению | [06](06-replica-set.md#арбитры-hidden-и-delayed) |
| **Keyset-пагинация** | keyset pagination | Страница выбирается условием "после последнего увиденного ключа" по индексу, а не пропуском `skip`; стоимость не растёт с номером страницы | [04](04-indexes.md#составной-индекс-и-правило-esr) |
| **Initial sync** | initial sync | Начальная синхронизация нового узла: копирование данных и применение oplog | [06](06-replica-set.md#initial-sync) |
| **Jumbo-чанк** | jumbo chunk | Неделимый диапазон ключа, превысивший размер чанка; балансировщик не может его разделить | [09](09-sharding.md#jumbo-чанки) |
| **majority** | majority | Большинство голосующих узлов; значение write concern и read concern | [07](07-read-write-concerns.md#write-concern-сколько-узлов-подтверждают-запись) |
| **Majority commit point** | majority commit point | Точка oplog, до которой запись подтверждена большинством; по ней сервер знает, что откат невозможен | [07](07-read-write-concerns.md#-глубже-majority-commit-point-и-откуда-берётся-90-секунд) |
| **maxStalenessSeconds** | maxStalenessSeconds | Ограничение отставания secondary для read preference; минимум 90 секунд | [07](07-read-write-concerns.md#read-preference-с-какого-узла-читать) |
| **mongos** | mongos | Маршрутизатор шардированного кластера; приложение ходит через него | [09](09-sharding.md#устройство-кластера-mongos-config-servers-шарды) |
| **Multikey-индекс** | multikey index | Индекс по массиву: отдельный ключ на каждый различный элемент; в составном индексе массивом может быть только одно поле | [04](04-indexes.md#multikey-индексы) |
| **nReturned** | nReturned | Метрика explain: сколько документов вернул запрос | [04](04-indexes.md#индекс-и-explain-как-читать-план) |
| **ObjectId** | ObjectId | 12-байтный идентификатор по умолчанию: 4 байта секунд, 5 байт процесса, 3 байта счётчика; генерируется клиентом | [01](01-document-bson.md#поле-_id-и-objectid) |
| **Oplog** | oplog (operations log) | Журнал идемпотентных операций primary, из которого secondary забирают изменения | [06](06-replica-set.md#oplog-формат-и-идемпотентность), [фон](../../docs/00-foundations/04-replication.md) |
| **Outbox** | outbox | Паттерн: событие записывается в той же транзакции, что и данные, а отдельный читатель его публикует; убирает двойную запись | [10](10-change-streams-java.md#at-least-once-и-outbox) |
| **PojoCodecProvider** | PojoCodecProvider | Провайдер кодеков драйвера для обычных Java-классов; record работает без него | [10](10-change-streams-java.md#mongoclientsettings-и-кодеки) |
| **Post-image** | post-image | Образ документа после изменения, сохраняемый для change stream (с 6.0) | [10](10-change-streams-java.md#pre--и-post-images) |
| **Pre-image** | pre-image | Образ документа до изменения, сохраняемый для change stream (с 6.0) | [10](10-change-streams-java.md#pre--и-post-images) |
| **Priority** | priority | Приоритет члена replica set при выборах; `0` значит «никогда не primary» | [06](06-replica-set.md#приоритеты-stepdown-и-catchup) |
| **PSA** | primary-secondary-arbiter (PSA) | Топология с арбитром вместо третьего узла с данными; неявный write concern в ней `w: 1` | [07](07-read-write-concerns.md#умолчание-majority-и-оговорка-про-арбитров) |
| **Read concern** | read concern | Гарантия чтения: какую версию данных увидит запрос (`local`, `majority`, `linearizable`, `snapshot`) | [07](07-read-write-concerns.md#read-concern-какую-версию-данных-читать) |
| **Read preference** | read preference | Правило выбора узла для чтения: primary, secondary, nearest | [07](07-read-write-concerns.md#read-preference-с-какого-узла-читать) |
| **Replica set** | replica set | Один primary и несколько secondary с копиями данных, синхронизируемыми через oplog | [06](06-replica-set.md#участники-и-их-роли), [фон](../../docs/00-foundations/04-replication.md) |
| **Resume token** | resume token | Токен позиции события в change stream; сохранив его после обработки, поток можно продолжить | [10](10-change-streams-java.md#resume-token-и-точка-старта) |
| **Rollback** | rollback | Откат записей, не дошедших до большинства, после смены primary; откаченное сохраняется в `/data/db/rollback` | [06](06-replica-set.md#rollback-когда-подтверждённая-запись-исчезает), [07](07-read-write-concerns.md#rollback-и-w-1) |
| **SBE** | slot-based execution engine (SBE) | Новый исполнитель запросов; часть стадий (`$group`, `$lookup` сразу за фильтром) идёт через него | [05](05-aggregation.md#-глубже-sbe-и-explain-в-80), [04](04-indexes.md#-глубже-как-планировщик-выбирает-план-и-что-такое-sbe) |
| **Scatter-gather** | scatter-gather (broadcast) | Запрос без ключа шардирования идёт на все шарды, результаты склеиваются (`SHARD_MERGE`) | [09](09-sharding.md#targeted-и-scatter-gather-запросы) |
| **Snapshot isolation** | snapshot isolation | Изоляция транзакции MongoDB: чтение из снимка на момент первой операции; это не сериализуемость | [08](08-transactions.md#snapshot-isolation) |
| **SORT** | SORT | Стадия плана: сортировка в памяти, которой избегают порядком полей индекса | [04](04-indexes.md#составной-индекс-и-правило-esr) |
| **syncdelay** | syncdelay | Период checkpoint в секундах (по умолчанию 60) | [02](02-wiredtiger.md#checkpoint) |
| **Time-series коллекция** | time series collection | Специальная коллекция, сама группирующая измерения в корзины по `timeField` и `metaField` | [03](03-data-modeling.md#паттерн-bucket) |
| **Targeted-запрос** | targeted query | Адресный запрос: по ключу шардирования идёт на один шард (`SINGLE_SHARD`) | [09](09-sharding.md#targeted-и-scatter-gather-запросы) |
| **TransientTransactionError** | TransientTransactionError | Метка ошибки: транзакцию можно повторить целиком | [08](08-transactions.md#writeconflict-и-метки-ошибок) |
| **TTL-индекс** | TTL index | Индекс, по которому фоновый монитор раз в 60 секунд удаляет устаревшие документы | [04](04-indexes.md#ttl-индексы) |
| **UnknownTransactionCommitResult** | UnknownTransactionCommitResult | Метка ошибки: исход коммита неизвестен, повторять нужно только коммит | [08](08-transactions.md#writeconflict-и-метки-ошибок) |
| **w: 1** | w: 1 | Write concern: подтверждение одного узла (primary); запись может исчезнуть при rollback | [07](07-read-write-concerns.md#rollback-и-w-1) |
| **Wildcard-индекс** | wildcard index | Индекс по заранее неизвестным полям документа | [04](04-indexes.md#wildcard-text-и-2dsphere) |
| **WiredTiger** | WiredTiger | Движок хранения MongoDB по умолчанию | [02](02-wiredtiger.md#место-wiredtiger-в-mongod), [фон](../../docs/00-foundations/07-storage-engines.md) |
| **withTransaction** | withTransaction | Callback API драйвера: повторяет транзакцию при временных ошибках (до 120 секунд, без пауз) | [08](08-transactions.md#withtransaction-или-core-api) |
| **Write concern** | write concern | Гарантия записи: сколько узлов подтверждают запись, с журналом ли (`j`) и сколько ждать (`wtimeout`) | [07](07-read-write-concerns.md#write-concern-сколько-узлов-подтверждают-запись) |
| **WriteConflict** | WriteConflict (код 112) | Ошибка: второй писатель того же документа внутри транзакции; несёт метку `TransientTransactionError` | [08](08-transactions.md#writeconflict-и-метки-ошибок) |
| **Аккумулятор** | accumulator | Оператор стадии `$group` (`$sum`, `$avg` и другие), сворачивающий группу в значение | [05](05-aggregation.md#unwind-и-group) |
| **Антипаттерн** | anti-pattern | Типичная ошибка модели: неограниченный массив, множество коллекций, раздутые документы, лишний `$lookup` | [03](03-data-modeling.md#антипаттерны) |
| **Арбитр** | arbiter | Член replica set, который голосует, но не хранит данные; может перевести неявный write concern в `w: 1` | [06](06-replica-set.md#арбитры-hidden-и-delayed) |
| **Атомарность одного документа** | single-document atomicity | Запись одного документа атомарна без транзакции, включая вложенные поля и массивы | [01](01-document-bson.md#атомарность-одного-документа), [08](08-transactions.md#сначала--можно-ли-без-транзакции) |
| **Балансировщик** | balancer | Фоновый процесс, переносящий чанки между шардами для выравнивания объёма данных | [09](09-sharding.md#чанки-и-балансировщик) |
| **Блокирующая стадия** | blocking stage | Стадия, которой нужен весь вход, прежде чем что-то отдать (например `$group`, `$sort`); память ограничена 100 МБ | [05](05-aggregation.md#память-100-мб-и-allowdiskuse) |
| **Большинство** | majority | Больше половины голосующих узлов; считается от голосующих (их до 7) | [06](06-replica-set.md#участники-и-их-роли) |
| **Валидатор** | validator | Правило коллекции (например `$jsonSchema`), по которому сервер отклоняет документы неправильной формы | [03](03-data-modeling.md#версия-схемы-и-валидация) |
| **Версионирование схемы** | schema versioning | Паттерн: `schemaVersion` в документе, чтобы старые и новые формы жили вместе | [03](03-data-modeling.md#версия-схемы-и-валидация) |
| **Встраивание** | embedding | Хранение связанных данных внутри документа родителя | [03](03-data-modeling.md#встраивание-или-ссылка) |
| **Выражения агрегации** | aggregation expressions | Язык вычисления значений во всех стадиях, кроме `$match` | [05](05-aggregation.md#операторы-запроса-и-выражения) |
| **Вытеснение** | eviction | Освобождение места в кэше WiredTiger: сначала фоном, при переполнении силами потоков запросов | [02](02-wiredtiger.md#вытеснение) |
| **Грязная страница** | dirty page | Страница кэша, изменённая в памяти и ещё не записанная на диск | [02](02-wiredtiger.md#вытеснение) |
| **Групповой коммит** | group commit | Запись журнала пачкой раз в 100 мс (`journalCommitInterval`) | [02](02-wiredtiger.md#журнал) |
| **Движок хранения** | storage engine | Компонент, отвечающий за хранение данных на диске и в памяти | [02](02-wiredtiger.md#место-wiredtiger-в-mongod) |
| **Документ** | document | Упорядоченный набор полей с вложенными документами и массивами | [01](01-document-bson.md#документ-и-коллекция) |
| **Журнал** | journal | Журнал WiredTiger для восстановления после сбоя между checkpoint | [02](02-wiredtiger.md#журнал) |
| **Зона** | zone (zone sharding) | Привязка диапазонов ключа к группам шардов | [09](09-sharding.md#зоны) |
| **Идемпотентность** | idempotency | Свойство операции oplog давать тот же результат при повторном применении (`$inc` хранится итоговым значением) | [06](06-replica-set.md#oplog-формат-и-идемпотентность) |
| **Индекс** | index | B-дерево, где ключ — значения полей, а значение — ссылка на документ | [04](04-indexes.md#индекс-и-explain-как-читать-план) |
| **Кардинальность** | cardinality | Число различных значений ключа шардирования; основа делимости чанков | [09](09-sharding.md#ключ-шардирования-кардинальность-частота-монотонность) |
| **Catchup** | catchup | Фаза после выборов, когда новый primary догоняет oplog остальных | [06](06-replica-set.md#приоритеты-stepdown-и-catchup) |
| **Ключ шардирования** | shard key | Поле (или поля), по которому документы распределяются по шардам | [09](09-sharding.md#ключ-шардирования-кардинальность-частота-монотонность), [фон](../../docs/00-foundations/05-partitioning.md) |
| **Кластерное время** | cluster time | Логические часы кластера, по которым упорядочиваются операции сессий | [07](07-read-write-concerns.md#причинная-согласованность-читать-свои-записи-с-secondary) |
| **Коллекция** | collection | Набор документов; схему сама не навязывает | [01](01-document-bson.md#документ-и-коллекция) |
| **Кэш WiredTiger** | WiredTiger cache | Память под страницы: по умолчанию max(50 % × (RAM − 1 ГБ), 256 МБ) | [02](02-wiredtiger.md#кэш-wiredtiger) |
| **Многодокументная транзакция** | multi-document transaction | Атомарная запись нескольких документов в сессии; дорогая, поэтому короткая | [08](08-transactions.md#сессия-и-жизненный-цикл-транзакции) |
| **Монотонность** | monotonicity | Свойство ключа расти со временем; монотонный ranged-ключ направляет все вставки в один шард | [09](09-sharding.md#ключ-шардирования-кардинальность-частота-монотонность) |
| **Окно oplog** | oplog window | Промежуток времени, который умещается в oplog; зависит от скорости записи | [06](06-replica-set.md#размер-и-окно-oplog), [10](10-change-streams-java.md#окно-oplog-и-changestreamhistorylost) |
| **Оконные функции** | window functions | Вычисления по окну соседних документов (`$setWindowFields`) | [05](05-aggregation.md#facet-bucket-и-оконные-функции) |
| **Оптимизатор конвейера** | aggregation pipeline optimizer | Переносит `$match` вперёд, склеивает соседние стадии, читает только нужные поля | [05](05-aggregation.md#оптимизатор-конвейера) |
| **Оптимистическая блокировка** | optimistic locking | Условное обновление с проверкой поля `version`: конфликт обнаруживается, а не блокируется | [08](08-transactions.md#сначала--можно-ли-без-транзакции) |
| **Орфанный документ** | orphaned document | Документ, оставшийся на шарде после миграции диапазона, хотя принадлежит другому | [09](09-sharding.md#-глубже-миграция-диапазона-и-орфанные-документы) |
| **Основной шард** | primary shard | Шард, на котором лежат несшардированные коллекции базы | [09](09-sharding.md#смена-ключа-и-перенос-коллекций) |
| **Паттерн attribute** | attribute pattern | Массив пар `{k, v}` вместо множества разных полей: хватает одного индекса | [03](03-data-modeling.md#паттерны-polymorphic-и-attribute) |
| **Паттерн bucket** | bucket pattern | Группировка многих мелких записей в один документ-корзину | [03](03-data-modeling.md#паттерн-bucket) |
| **Паттерн computed** | computed pattern | Хранение готовых счётчиков и сумм, обновляемых коммутативным `$inc` | [03](03-data-modeling.md#паттерн-computed) |
| **Паттерн extended reference** | extended reference pattern | Копирование редко меняющихся полей связанной сущности, чтобы не делать `$lookup` | [03](03-data-modeling.md#паттерн-extended-reference) |
| **Паттерн outlier** | outlier pattern | Особая ветка для редких документов-гигантов, не раздувающая обычные | [03](03-data-modeling.md#паттерн-outlier) |
| **Дискриминатор** | discriminator | Поле, по значению которого различают виды документов в одной коллекции (например `type`) | [03](03-data-modeling.md#паттерны-polymorphic-и-attribute) |
| **Паттерн polymorphic** | polymorphic pattern | Разные виды сущностей в одной коллекции, различаемые полем-дискриминатором | [03](03-data-modeling.md#паттерны-polymorphic-и-attribute) |
| **Паттерн subset** | subset pattern | Витрина фиксированного размера внутри родителя (`$push` с `$each`, `$sort`, `$slice`) | [03](03-data-modeling.md#паттерн-subset) |
| **Пересечение индексов** | index intersection | Использование двух индексов одним запросом | [04](04-indexes.md#скрытые-индексы-и-пересечение-индексов) |
| **Покрывающий запрос** | covered query | Запрос, отвечающий только из индекса (`PROJECTION_COVERED`, 0 документов) | [04](04-indexes.md#покрывающие-запросы) |
| **Правило ESR** | ESR (Equality, Sort, Range) | Порядок полей составного индекса: равенство, сортировка, диапазон | [04](04-indexes.md#составной-индекс-и-правило-esr) |
| **Причинно согласованная сессия** | causally consistent session | Сессия, где чтение видит собственные записи и порядок событий даже с secondary | [07](07-read-write-concerns.md#причинная-согласованность-читать-свои-записи-с-secondary) |
| **Протокол pv1** | protocol version 1 (pv1) | Протокол выборов: терм, голоса большинства за кандидата с достаточно свежим oplog | [06](06-replica-set.md#выборы-по-протоколу-pv1), [фон](../../docs/00-foundations/06-consensus-membership.md) |
| **Рабочий набор** | working set | Данные и индексы, которые запросы трогают регулярно; должны помещаться в кэш | [02](02-wiredtiger.md#кэш-wiredtiger) |
| **Скрытый индекс** | hidden index | Индекс, невидимый планировщику, но поддерживаемый; сначала скрывают, потом удаляют | [04](04-indexes.md#скрытые-индексы-и-пересечение-индексов) |
| **Снимок** | snapshot | Согласованная версия данных на момент времени; основа MVCC и транзакций | [02](02-wiredtiger.md#mvcc-и-снимки) |
| **Составной индекс** | compound index | Индекс по нескольким полям; порядок полей подбирают по ESR | [04](04-indexes.md#составной-индекс-и-правило-esr) |
| **Составной ключ шардирования** | compound shard key | Ключ из нескольких полей: сочетает адресность и делимость | [09](09-sharding.md#hashed-ranged-и-составной-ключ) |
| **Ссылка** | referencing | Хранение связанных данных отдельным документом по идентификатору | [03](03-data-modeling.md#встраивание-или-ссылка) |
| **Терм** | term | Номер эпохи выборов в replica set | [06](06-replica-set.md#выборы-по-протоколу-pv1) |
| **Уникальный индекс** | unique index | Индекс, запрещающий дубликаты; по необязательному полю делают частичным | [04](04-indexes.md#уникальные-и-частичные-индексы) |
| **Условное обновление** | conditional update | Обновление с условием в фильтре, атомарное на одном документе | [08](08-transactions.md#сначала--можно-ли-без-транзакции) |
| **Цепочка обновлений** | update chain | Список версий значения в памяти, по которому MVCC выбирает видимую читателю | [02](02-wiredtiger.md#mvcc-и-снимки) |
| **Чанк** | chunk | Диапазон ключа шардирования; по умолчанию 128 МБ, в документации 8.0 чаще — «диапазон» | [09](09-sharding.md#чанки-и-балансировщик) |
| **Частичный индекс** | partial index | Индекс по подмножеству документов; используется, только если из запроса следует его фильтр | [04](04-indexes.md#уникальные-и-частичные-индексы) |
| **Частота** | frequency | Доля документов с одним значением ключа шардирования; высокая частота даёт горячий шард | [09](09-sharding.md#ключ-шардирования-кардинальность-частота-монотонность) |
| **Шард** | shard | Часть кластера, хранящая подмножество данных; сам является replica set | [09](09-sharding.md#устройство-кластера-mongos-config-servers-шарды), [фон](../../docs/00-foundations/05-partitioning.md) |
| **Шардированный кластер** | sharded cluster | Шарды, config servers и mongos вместе | [09](09-sharding.md#устройство-кластера-mongos-config-servers-шарды) |

Термины, которых здесь нет, обычно являются именами параметров (`syncdelay`, `electionTimeoutMillis`) или командами (`rs.status()`): их ищут по самому имени, перевода у них нет.

---

[оглавление раздела](README.md)
