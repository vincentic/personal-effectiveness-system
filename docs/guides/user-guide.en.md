# Personal Effectiveness System · User Guide (English)

This guide helps you put the "daily execution → weekly review → monthly refactor" loop into practice. The system includes Django, Go/Gin, Java/Spring Boot, FastAPI, and Flask implementations, plus shared Vue and React frontends. Gin, Spring Boot, and Flask provide server-rendered pages, Django provides a Session page, and FastAPI can host Vue 3 and the REST API at one origin.

- [简体中文](user-guide.zh-CN.md) · [繁體中文](user-guide.zh-TW.md) · [日本語](user-guide.ja.md)
- [Back to project README](../../README.md)

## 1. Sign in and switch between PC / Mobile

1. Open your chosen version's root URL (Django `:8000/`, Spring Boot `:8080/`, Gin `:8081/`, Flask page app `:8001/`; FastAPI `:8000/` after building the frontend; React dev page `:5174/`) and sign in with your account.
2. **Django / Spring Boot / Gin dual view**: the server picks PC or Mobile by browser User-Agent and serves a touch-friendly mobile view on phones.
3. **Manual switch in the first three editions**:
   - On PC, click "📱 Mobile" in the top bar to go mobile.
   - On mobile, click "🖥 PC" in the header to go back; the bottom bar also has an "Auto" option.
   - Your choice is stored in a browser cookie (1 year) and persists on next visit.
   - Switching reloads the current page. Unsaved form input will be lost, so save before switching.

4. **FastAPI page**: PC and phone share a responsive Vue 3 interface; on phones the navigation collapses into a menu. The page is served by FastAPI at the same origin, and `/docs` is the interactive API documentation.
5. **React page**: it provides the same workflows as Vue, can be deployed independently, and defaults to `http://127.0.0.1:5174/` in development.

## 2. Daily Execution Checklist (3 min morning + 5 min evening)

Go to **Daily** and click "New daily plan". The page has five sections:

1. **Today's direction**: date, sleep hours, energy (low/medium/high), and your **Most Important Task (MIT)** — decide the outcome first, then schedule time.
2. **Blocks & time boxes**: for 2–4 blocks fill in name, time-box start/end, input, key action, and verifiable output. Check progress at the end of a box; you don't have to force completion. Tick done as you go.
3. **Before / during / after checks**: three items each — before (know the deliverable / materials ready / notifications handled), during (one block at a time / shrink the next step when stuck / record distractions), after (visible output / active recall / mark the next step).
4. **Life maintenance**: water & food, movement, cleaning/tidying, real conversation or outdoors — not about a perfect score, just keeping the baseline.
5. **Evening review**: what you actually finished, the biggest resistance, tomorrow's smallest starting action, and one thing worth gratitude.

After saving, the list shows each day's block progress (done/total) and checklist completion. Django lets you toggle a block directly from the list; in Gin, Spring Boot, and Flask, edit the daily plan to change its completion status. Page apps let you edit or delete records.

**Key points**: one MIT only; every block needs a verifiable output; the evening review asks what was *actually* completed, not what was planned.

## 3. Weekly Review Checklist (30–45 min per week)

Go to **Weekly** and click "New weekly review". Four sections:

1. **This week in facts**: for Work/Project, Learning/Skills, Body/Energy, Relationships/Life — record outputs as **facts, not feelings**, and mark status (smooth / blocked / paused).
2. **Task-flow cleanup**: sort tasks into To-do, Doing, Done, and Delete/Delegate/Delay. The point is an outflow — don't let tasks only accumulate.
3. **Next week design**: at most 1–3 most important outcomes, deep-work time to protect (date/slot), environment or materials to prepare ahead, and one If–Then plan ("If …, then I will …").
4. **Review questions**: which block got sharper, what cost a lot but returned little, which time box was most mis-estimated, whether there was rest/joy/real connection, and what to continue/adjust/stop next week.

**Key point**: the weekly review matters more than the weekly plan — aim for a clearer, lighter, more effective next week, not a fuller one.

## 4. Monthly Refactor Checklist (45–60 min per month)

Go to **Monthly** and click "New monthly refactor". Four sections:

1. **Goals & value check**: this month's most important outcome, whether it is still worth investing in (yes/adjust/stop + reason), the most valuable investment, and the biggest opportunity cost.
2. **System health check**: score six modules 1–5 — direction clarity, task load / work in progress, block quality, time-box realism, sleep/energy/mood, review & feedback quality — and note the change needed. The dashboard shows the average.
3. **Three improvements for next month**: keep (what works), cut (low value), experiment (one smallest new change).
4. **Quarterly calibration** (optional): skills turned into showcase work, work closer to long-term direction, more autonomy in life, enough recovery and connection, whether to continue/redirect/stop a long-term investment.

**Key point**: the ultimate standard is — important things keep moving, life's baseline stays stable, attention is self-directed, and the system itself is not a burden.

## 5. Implementations and shared data

Gin, Spring Boot, FastAPI, and Flask use the shared v2 schema in `db/schema.sql`, with data isolated by account. Django uses its own ORM migrations for equivalent business entities. FastAPI, Spring Boot, Gin, and Flask API follow the same API Token contract; each page app has its own session mechanism.

- **Page users**: sign in via Session / signed cookie; data is isolated per account — nobody else can see your lists.
- **API users**: Vue / React frontends and API clients get a Token from `POST /api/auth/login`, then send `Authorization: Bearer <token>`; Tokens work across all four API implementations. See the [API contract](../data/api-contract.md).
- Default ports: Spring Boot 8080, Gin 8081, Django / FastAPI 8000, Flask page app 8001, Flask API 8002, Vue 5173, React 5174.

## 6. FAQ

**Q: Does switching Mobile/PC lose data?**
No. Switching only changes the display template and browser preference. Input not yet saved is not auto-saved, so save before leaving.

**Q: Can I have multiple entries for one day/week/month?**
No. A unique constraint on (account, date/week/month) allows one daily plan per day, one weekly review per week, one monthly refactor per month. Edit the existing entry to change it.

**Q: How are detail items (blocks, domains, task flows, health items) saved?**
They are saved with the parent list. Editing detail rows and saving replaces the old details as a whole — you don't save each item separately.

**Q: How do I backfill a day I missed?**
In the Daily list click "New daily plan" and pick a past date; if that day already has a record, edit it instead.

**Q: Is content hard to read on my phone?**
Django / Spring Boot / Gin use separate mobile templates; Flask, Vue, and React pages use responsive layouts. Content adapts to screen width.

**Q: Can several people use it at once?**
Yes, data is isolated per account. User management is admin-only (`/admin/users/` or the equivalent page).

## 7. Suggested rhythm

- Morning: 3 minutes to write the MIT and time boxes;
- End of each box: 30 seconds to tick and check;
- Evening: 5 minutes for the four review questions;
- Weekly: fixed 30–45 minutes for review;
- Monthly: fixed 45–60 minutes for refactor.

After a quarter, use the monthly "system health" trend and the weekly "continue/adjust/stop" notes to judge whether the system is serving you.
