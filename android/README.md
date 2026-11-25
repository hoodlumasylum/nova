# Nova for Android (class)

A standalone Nova app for teaching. Students install the same app on their own phones. Nova talks with each student one-to-one. She stays resting until the teacher turns class on, or allows home use.

This is the same Nova already used in CodeAque Kids mode: gold face, moving mouth, Heart voice, on-phone brain, and Simple English Wikipedia when online. It is a separate app, so it can sit next to CodeAque.

Version **1.0.3**.

## Install (one APK)

1. On the phone, open this file: **Nova.apk**.
2. If the phone asks, allow it to install from this source.
3. Open **Nova**.

There is no zip file and no sign-in.

**Teacher first:** the teacher should open the app and set a teacher PIN before students join.

If you already have **1.0.0**, install a later APK once by hand. From 1.0.1 onwards, Nova checks for a newer Android release when it opens and can install **Nova.apk** with one tap (you may need to allow Nova to install apps). If the phone is offline, the check is skipped quietly.

## Teacher

1. Tap **I am the teacher**.
2. Choose a teacher PIN of 6 to 16 letters or numbers. Capital letters matter. Keep it private. It is saved only on this phone, as a salted hash — never in the project, and never on the class switch.
3. After that PIN, you land on **Just Nova**: full-screen Nova with the mic, as your own personal assistant. There is no class lock on this screen, and no class controls. Tap **Teacher / Class tools** when you want the student PIN, class on/off, or lesson tools. **Just Nova** on that tools screen brings you back.
4. In Teacher / Class tools you will see a big **student PIN** (5 or 6 digits). Tell the class that PIN. **New student PIN** makes a new one and locks out the old one, including old start or home codes.
5. Tap **Start class** when the lesson begins, and **End class** when it finishes. Class stays on for 3 hours unless you end it sooner.
6. **Allow home use for everyone** lets the whole class use Nova at home for 7 days, or until you turn it off.
7. **Students** lets you turn home use on or off for one student. They appear after they join.
8. **Class mode** — type or say a topic. Nova makes a short, kid-safe lesson in simple English:
   - a short explanation
   - **Flashcards** (5 to 10 cards; Nova reads them aloud)
   - **Quick-fire quiz** (one question at a time, a timer, a big reveal, and a score tally)
   - **Step-by-step lesson** (3 to 5 short steps with Next)
   - **Share homework** (opens the Android share sheet so you can send the sheet)
9. **Just Nova** — back to your personal full-screen Nova.
10. If the internet is down, **Show a start code**. A student can type that code to get access until it runs out (3 hours for class, or 7 days for a home pass). These codes are tied to the current student PIN.

## Student

Students can use these tools only while the teacher has allowed access (class is on, or home use is allowed, or a start/home code is still valid).

1. Tap **I am a student**.
2. Type the student PIN and your first name, then **Join**. If the teacher makes a new student PIN, you will need to join again.
3. If class is off and home use is not allowed, you will see: **Nova is resting until class**.
4. When access is allowed, you get a simple class home:
   - **Today's topic** — the recap the teacher pushed
   - **Practice flashcards**
   - **Self-quiz** with your own score
   - **Ask Nova about the lesson**
   - **My progress** (quizzes finished and scores, stored only on this phone)
   - **Talk with Nova**

## First-time downloads

The first time Nova speaks in her own voice, the phone downloads her voice (about 360 MB). An optional on-phone brain (about 800 MB) can also be downloaded. After that, talking works offline.

She may also look up a short fact on Simple English Wikipedia when the phone is online. The class on/off switch also needs the internet, unless a student is using a start or home code. If the on-phone brain is not downloaded, Class mode still builds a lesson from Wikipedia or a simple fallback.

## Parent / Teacher

On the student side, **Parent / Teacher** is on the class home, the resting screen, and the join screen. It works at home even when class is off. The child's own class tools still follow the teacher's class on/off switch.

1. The first time, a parent chooses a parent PIN of 6 to 16 letters or numbers (capital letters matter). It is saved only on that phone, as a salted hash — never in the project, and never on the class switch. It is not the student PIN or the teacher's PIN.
2. After the PIN, the parent can see the child's progress, homework from class, and any note the teacher wrote for parents, and can share a progress report with the Android share sheet.
3. **Teach a topic at home** gives the parent the same teaching tools (explanation, flashcards, quick-fire quiz, step-by-step lesson, and homework) to use with their child. Those home lessons stay on that phone.

Teachers can write a **note for parents** in Class mode. It is included when they share homework, and it shows in Parent / Teacher on the child's phone once the lesson has reached that phone.

This app is free for a class teacher and for parents at home. A paid option is for schools, colleges, universities and other organisations.

## Safety

Nova stays clean and simple. She will not talk about religion, god, or conspiracies, and she only calls herself Nova.

## Honest limits

- The live class switch uses a free public page (rentry.co). Nobody needs an account or an API key. If that page cannot be reached, use a start or home code, or the last update saved on the phone.
- A very determined person who unpacks the app could find the switch address and the write codes. Keep the teacher PIN private. For a classroom this is a simple honour-system switch, not a bank lock.
- If a phone is offline, a home-use grant can last until its time runs out (or up to about a day after it last checked in).
- This version is for one class (the class in this app).
- Student progress stays on that student's phone. The teacher cannot see it from the class switch.
- Live update checks use the public GitHub releases list and only pick a newer tag like `v1.0.1-android` that has a **Nova.apk** file.
- The APK was built and signed, and the PIN, update-tag, and lesson-pack logic were tested on the computer. It has not yet been run on a real phone or in an emulator (the emulator image was not available on the build machine). Class tools, sharing, and the in-app installer have not been tapped through on a device.
