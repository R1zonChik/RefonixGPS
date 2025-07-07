# RefonixGPS

<div align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.16.5-green?style=for-the-badge" alt="Minecraft Version">
  <img src="https://img.shields.io/badge/Java-8+-orange?style=for-the-badge" alt="Java Version">
  <img src="https://img.shields.io/github/v/release/yourusername/RefonixGPS?style=for-the-badge" alt="Release">
  <img src="https://img.shields.io/github/downloads/yourusername/RefonixGPS/total?style=for-the-badge" alt="Downloads">
</div>

## 🎯 Описание

**RefonixGPS** - это социальная GPS система для Minecraft серверов, которая позволяет игрокам отслеживать друзей с их разрешения. Плагин создан специально для РП серверов и предоставляет удобную навигацию в реальном времени.

## ✨ Особенности

- 🔐 **Система разрешений** - никто не может отслеживать без согласия
- 🧭 **Action Bar навигация** - стрелки направления и расстояние
- 📍 **Отображение координат** - точные координаты всех друзей
- 💾 **Автосохранение** - все данные сохраняются в YAML
- 🎨 **RGB градиенты** - красивые цветные сообщения
- 🔔 **Уведомления** - когда друзья заходят/выходят
- 🔊 **Звуковые эффекты** - при приближении к друзьям
- 📚 **История перемещений** - отслеживание маршрутов
- ⚙️ **Полная настройка** - все через config.yml

## 📋 Команды

| Команда | Описание |
|---------|----------|
| `/gps add <игрок>` | Отправить запрос на отслеживание |
| `/gps accept` | Разрешить отслеживание |
| `/gps deny` | Отклонить запрос |
| `/gps list` | Показать всех друзей с координатами |
| `/gps track <игрок>` | Выбрать для навигации |
| `/gps toggle` | Включить/выключить навигацию |
| `/gps notifications` | Включить/выключить уведомления |
| `/gps history <игрок>` | История перемещений |
| `/gps remove <игрок>` | Убрать из отслеживания |

## 🚀 Установка

1. **Скачайте** последнюю версию из [Releases](https://github.com/yourusername/RefonixGPS/releases)
2. **Поместите** JAR файл в папку `plugins/`
3. **Перезапустите** сервер
4. **Настройте** `config.yml` под свои нужды

## ⚙️ Конфигурация

```yaml
settings:
  update-interval: 10          # Интервал обновления (тики)
  max-distance: 1000          # Максимальная дистанция
  proximity-distance: 10      # Расстояние для уведомлений
  proximity-sound: true       # Звуки приближения
  join-notifications: true    # Уведомления входа/выхода
  history-enabled: true       # Включить историю
  history-max-entries: 50     # Максимум записей в истории
```

## 🎮 Как использовать

### Базовое использование:
```
1. Игрок1: /gps add Игрок2
2. Игрок2: /gps accept
3. Игрок1: /gps track Игрок2
4. Теперь видишь стрелку и расстояние!
```

### Дополнительные функции:
- Используй `/gps notifications` чтобы отключить уведомления
- Команда `/gps history` покажет где был игрок
- `/gps toggle` быстро включает/выключает навигацию

## 🔧 Требования

- **Minecraft:** 1.16.5+
- **Сервер:** Bukkit/Spigot/Paper
- **Java:** 8+

## 🤝 Поддержка

Нашли баг или есть предложения? 

- 🐛 [Создать Issue](https://github.com/r1zonchik/RefonixGPS/issues)
- 💬 [Обсуждения](https://github.com/r1zonchik/RefonixGPS/discussions)

## 📈 Статистика

![GitHub stars](https://img.shields.io/github/stars/r1zonchik/RefonixGPS?style=social)
![GitHub forks](https://img.shields.io/github/forks/r1zonchik/RefonixGPS?style=social)
![GitHub issues](https://img.shields.io/github/issues/r1zonchik/RefonixGPS)

## 📄 Лицензия

Этот проект распространяется под лицензией MIT. Подробности в файле [LICENSE](LICENSE).

## 👨‍💻 Автор

**RefonixStudio** - [@rizonchik](https://github.com/r1zonchik)

---

<div align="center">
  <b>Сделано с ❤️ для крутых РП серверов!</b>
</div>
