<div align="center">

# ShepherdApp

### 📖 The Digital Ministry Companion for Pastors

*Prepare. Organize. Study. Preach.*

ShepherdApp is an AI-powered Android application designed to help pastors, ministers, teachers, and church leaders manage every stage of sermon preparation and ministry from one place.

---

</div>

## ✨ Features

### 📚 Sermon Organizer

Keep every sermon, teaching, and Bible study neatly organized.

* Create sermon series
* Organize teachings by topic, book, season, or event
* Categorize seminars and conference notes
* Quickly search through your ministry library

---

### 🤖 AI Writing Assistant

Transform rough ideas into polished ministry content.

Use AI to:

* Improve sermon drafts
* Rewrite devotionals
* Expand outlines into complete sermons
* Simplify difficult theological concepts
* Generate titles and key points
* Refine seminars and Bible study notes

---

### 🎤 Preach Mode

A distraction-free preaching experience.

* Clean presentation interface
* Large readable text
* Quick navigation through sermon points
* Designed for use from the pulpit

---

### 📖 Study Board

A dedicated workspace for Bible study and sermon preparation.

* Build sermon outlines
* Record study notes
* Collect references
* Organize research in one place

---

### 📅 Ministry Calendar

Never lose track of ministry events.

Schedule:

* Church services
* Conferences
* Prayer meetings
* Bible studies
* Counseling appointments
* Ministry deadlines

---

### 📄 Document Viewer

Open and review ministry documents directly inside the app.

Perfect for:

* Sermons
* Seminar notes
* Lesson plans
* Church documents

---

### 🕘 History

Every AI interaction and document revision is automatically stored, making it easy to revisit previous work whenever needed.

---

## 🚀 Getting Started

### Prerequisites

Before running the project, ensure you have:

* Android Studio (latest stable version)
* Android SDK
* A valid Gemini API Key

---

## Installation

1. Clone the repository

```bash
git clone https://github.com/yourusername/ShepherdApp.git
```

2. Open Android Studio.

3. Select **Open Existing Project**.

4. Choose the cloned project folder.

5. Allow Android Studio to finish indexing and install any required dependencies.

6. Create a `.env` file in the project root.

```env
GEMINI_API_KEY=YOUR_API_KEY
```

See `.env.example` for the expected format.

7. Remove the following line from:

```
app/build.gradle.kts
```

```kotlin
signingConfig = signingConfigs.getByName("debugConfig")
```

8. Build and run the application on an Android emulator or physical device.

---

## 🛠 Built With

* Kotlin
* Jetpack Compose
* Android Studio
* Google Gemini API
* Material Design 3

---

## 📱 Vision

ShepherdApp exists to simplify sermon preparation and ministry administration through thoughtful design and modern AI.

Instead of juggling multiple note-taking apps, calendars, document editors, and AI tools, ShepherdApp brings everything together into a single ministry workspace where pastors can prepare messages, organize teachings, plan events, study Scripture, and preach with confidence.

---

## 🤝 Contributing

Contributions are welcome!

If you'd like to improve ShepherdApp:

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Open a Pull Request

---

## 🔒 Environment Variables

Create a `.env` file containing:

```env
GEMINI_API_KEY=YOUR_API_KEY
```

Your API key should never be committed to source control.

---

<img width="576" height="1280" alt="photo_2026-07-20_09-30-23" src="https://github.com/user-attachments/assets/2d4a9853-a4df-44c5-9a2d-1ca191271bbc" />


<div align="center">

**Helping pastors spend less time managing files and more time shepherding people.**

Made with ❤️ for ministry.

</div>
