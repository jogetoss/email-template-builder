# Email Template Builder: notes for agents and contributors

[README.md](README.md) says what the plugin is and how to use it. This file has the detail: how the code is laid out, how to build and test it, the rules the renderer keeps, every block, and the DX 9 behaviour the code works around.

## Working in this repo

- **Build and test:** `mvn verify` (Java 17). It needs Joget `9.0-SNAPSHOT` (`wflow-core`, `wflow-commons`, `wflow-plugin-base`) built locally or available from Joget's repositories in your Maven settings. All tests must pass before a change is done.
- **Where things are:**
  - `email-template-builder-api/`: the block SPI (`org.joget.etb.api`), the only package third-party blocks compile against.
  - `email-template-builder/src/main/java/org/joget/etb/`: the builder (`EmailTemplateBuilder`), the Activator, and the block, starter and component catalogs.
    - `lib/`: the blocks, the Send Templated Email tool and the `#emailTemplate#` hash variable.
    - `render/`: the renderer (JSON design → MJML → HTML and text), merge-field resolution, the URL policy.
    - `composer/`: the optional AI Composer (compiled only with the `ai-composer` profile).
  - `email-template-builder/src/main/resources/`:
    - `properties/blocks/`: one property page per block.
    - `templates/starters/`, `templates/components/`: the galleries, one JSON each plus `index.json`.
    - `resources/emailtemplatebuilder.js`: the canvas.
    - `messages/emailTemplateBuilder.properties`: labels, and the version from the POM.
  - `email-template-builder/src/test/resources/golden/`: rendering snapshots.
- **Starters and components** are the JSON files above; edit them directly. `StarterCatalogTest` checks every one (see [Starter templates](#starter-templates)). Components are configurable blocks, not HTML snippets, and never use `{var.…}` or `{row.…}`.
- **Don't weaken the escaping rules** in [Rules that matter](#rules-that-matter): merge values are HTML-escaped once, links go through the URL allowlist, and a merge value is never read as a field. The tests pin each of these.
- **Builder messages stay quiet** when things work: one line, details on demand. Show more only when something needs attention.
- **Writing:** say "DX 9", with a space. Module and artifact names use hyphens.

## Modules

| Module | What |
|---|---|
| `email-template-builder-api` | The block SPI: `EmailBlock`, `DefaultEmailBlock`, `EmailRenderContext`, `EmailRow`. Third-party blocks compile against this jar. |
| `email-template-builder` | The OSGi bundle: builder, renderer, blocks and hash variable. It embeds `mjml4j` 1.2.2 (MIT) and `jfiveparse` 2.0.1 (Apache-2.0), and exports `org.joget.etb.api`. |

## Build

The build needs Java 17. It targets DX 9 (`wflow-core` 9.0-SNAPSHOT) and is jakarta-native.

```bash
mvn verify
```

The jar to install is `email-template-builder/target/email-template-builder-9.0-SNAPSHOT.jar`, about 570 KB.

**Versioning:**
- The version follows the Joget line the plugin needs: DX 9 onwards.
- It is set in the three POMs only. Every plugin's `getVersion()` reads `etb.version`, which Maven's resource filtering writes into `messages/emailTemplateBuilder.properties` from the POM.
- The manifest carries `Joget-Name` and `Joget-Description`.
- **Upgrading from `email-template-builder-1.0-SNAPSHOT.jar`:** uninstall it in **Manage Plugins** first. A jar with a new file name installs next to the old one, not over it. Templates are stored in the app and are not affected.

**Tests (190: 189 unit, 1 integration; 19 of them are the AI Composer's and need the `ai-composer` profile):**
- rendering rules and golden snapshots (`email-template-builder/src/test/resources/golden`)
- merge-value escaping against a stand-in that behaves like Joget's `AppUtil.processHashVariable`, and templates that include themselves
- HTML-to-text, the URL allowlist, and the record and tracking resolvers
- block metadata and the POM-driven version
- each data and component block: Datalist Table, Repeat, Steps, Progress bars, Facts list, Timeline, Stat tiles, Badge, Callout
- every starter and component (`StarterCatalogTest`)
- the Send Templated Email tool
- a probe of Joget's hash-variable sanitising
- `FelixBundleIT`, which installs the jar into Felix 7.0.5 with DX 9's own `felix_config.properties`, checks its 23 services and loads every class

When a block's output changes on purpose, or after upgrading mjml4j, review the diff and regenerate the snapshots:

```bash
mvn test -Dgolden.update=true
```

## Using it, in detail

1. Upload the jar in **Manage Plugins**.
2. In an app, create an **Email Template**. It gets an ID like `ET-001` and starts blank. In the builder:
   - **Templates** (toolbar) opens a gallery of ready-made designs with live thumbnails. Picking one replaces the design but keeps the ID, name and description. **Undo** reverts it, and you can switch designs at any time.
   - **Components** (toolbar) inserts ready-made parts; see [Components gallery](#components-gallery).
   - Edit text by clicking it on the canvas, and any block in its properties panel.
   - **Settings** holds the subject, preheader, template variables (see step 3) and the public server address.
   - **Email style** (toolbar), or a click on the page background, opens the theme: colours, font, width and the card's corners and shadow.
   - **Test data** (toolbar) holds a form record ID, which fills `#form.table.column#` merge fields, and a test value per template variable. It is kept in this browser per template and used only by the canvas, Preview, Send test and the size meter; real sends never see it.
   - **Preview** and **Send test** list any merge fields that came out empty.
   - **Send test** (toolbar) emails the current design, saved or not:
     - to the address(es) you type in its prompt, prefilled with your account's email (up to 5, separated by `;`)
     - with merge fields filled as in Preview, and the subject prefixed `[Test]`
     - through the app's Email Tool SMTP defaults, else the system SMTP; it shows any SMTP error
   - A **size meter** at the bottom-left of the canvas shows the sent email's HTML size against Gmail's ~102 KB clipping limit: amber from 80 KB, red from 102 KB.
   - DX 9 has no merge-field picker in builder property fields; type `#...#` directly.
3. Send it with the **Send Templated Email** process tool (recommended).
   - **Template variables** (Settings) are the email's inputs. Declare each with a name and a description, and use it as `{var.NAME}` anywhere: subject, blocks, links, filter parameters, Repeat cards.
   - The tool's **Template variables** grid maps each to a value. Merge fields are allowed; inside an **Iterator Process Tool**, use `{column}` of the iterated record, e.g. `boardId = {id}`. An unmapped variable is sent empty with a log warning.
   - `{row.NAME}` always means a Repeat's row, never a variable.
   - Pick the template, then set recipients exactly as in the Email Tool. It sends the template's subject, HTML **and a plain-text version** (`multipart/alternative`).
   - SMTP comes from this tool's own fields, else the app's **Email Tool Plugin Default Properties**, else the server's system SMTP.
   - Attachments, embedded images (`cid:`), ICS invites and retries work as in the Email Tool, whose property pages it reuses.

   Or use the stock **Email Tool** (HTML only):
   - **Message:** `#emailTemplate.ET-001?noescape#`, with **HTML** ticked.
   - **Subject:** `#emailTemplate.ET-001.subject#`.
   - **Plain text** (for another field): `#emailTemplate.ET-001.text#`.
   - The Email Tool has nowhere to map template variables, so `{var.…}` fields come out empty (with a log warning). Use `#…#` merge fields in templates meant for it.
   - It can't embed App Resource images either, so Image blocks using them are left out (with a log warning); use absolute image URLs.
   - A template ID that doesn't exist is left as written, e.g. `#emailTemplate.ET-nope#`.
   - A template that includes itself (`#emailTemplate.ET-001#` inside ET-001, or two templates including each other) renders once; the inner reference is left empty, with a log warning.

### Rules that matter
- **`?noescape` is required.** Without a format, Joget sanitises the value, which strips the Outlook markup, table attributes and head styles. The email also leaks its title into the body.
- **Merge fields** such as `#form.request.title#` or `#assignment.activityName#` can go anywhere in the text, subject, preheader and links. They are filled in with the process context, and **their values are HTML-escaped once**. A form value containing `<a href=...>` or `<script>` shows as text; it never becomes a live link or runs, and `Price < 100` shows as written. To do this the plugin gives each merge field without a format its own (`?html` in HTML, `?noescape` in the subject, text part and filter values), because Joget would otherwise run its HTML sanitiser first and escape `<` twice. A field you give a format (`#form.x?url#`) keeps it. A merge value is never read as a field either: a form value such as `{var.boardId}` prints as written.
- **Links** must be `http`, `https`, `mailto`, `tel`, `cid`, or a place in the same email (`#top`). Anything else, such as `javascript:` or `data:`, becomes `#` and produces a warning, and so does a merge field left unfilled in a link.
- **Images:** Send Templated Email embeds App Resource images as `cid:`. A Hero background is linked instead (see Hero below). Form images can also go through the Attachments with **Embed** ticked; Joget rewrites their URLs to `cid:`.
- **Known limits:**
  - The stock Email Tool sends HTML only, with no text/plain part; Send Templated Email sends both.
  - Gmail makes bare URLs clickable even when they're escaped.

## Blocks

Sizes are in px (the field label says so), and padding is one to four px values as in CSS. A bare number takes the unit: `14` is 14px, `8 32` is 8px 32px, and in a % field (Divider width, Facts label width) `35` is 35%. A Column's width needs its unit (`50%` or `200px`), since a bare number could be either. Any other value (`12pt`, `1.5em`) is ignored and the default is used. Blocks built on `DefaultEmailBlock` get the same rule through `prop()`.

| Block | MJML | Notes |
|---|---|---|
| Text | `mj-text` | Edited inline on the canvas. The optional **background colour** makes a full-width band, e.g. a header or footer. |
| Button | `mj-button` | Warns when the link has no address. **Space inside the button** sets the room around the label (smaller for buttons side by side, e.g. a 1 to 5 scale). |
| Image | `mj-image` | Pick or upload from **App Resources** (embedded as `cid:` by Send Templated Email, so no public server URL is needed; left out through the Email Tool), or an absolute URL, or `cid:` (Attachments with Embed). Optional link and width. Shows a labelled placeholder box until a source is set. |
| Navbar | `mj-navbar` | A row of text links (grid of label + link), wrapping on phones. Plain case by default. No hamburger menu: it only works in Apple Mail. |
| Custom HTML | `mj-text` | Your own markup (edited as code), and HTML from the app. Hash variable values are **not** escaped (add `?html` to `{var.…}`/`{row.…}` for the same); for app-generated HTML only (digests, change logs), never raw form input. Joget still strips scripts, unless the field carries `?noescape`: then nothing is sanitised, in the sent email **and in the builder** (canvas, Preview, Real rows), which run on the Joget site with the admin's session. Use `?noescape` inside a template only for values the app itself produces. |
| Datalist Table | `mj-table` | Data. Rows of an app datalist: line items (filter `order_id = #form.orders.id#`, bound as a query parameter) or a digest (no filter). Read as the process's user, so the datalist's permissions apply (none in a scheduled run: empty table plus a warning). Columns picked and ordered; sort; 1 to 100 rows; **cell values** as formatted text (default), formatter HTML (scripts stripped) or raw values; "when empty" text. **Datalist filter parameters** run the datalist's own filters as its page URL would (`fn_dateCreated = #date.DAY-1.yyyy-MM-dd#` and again with `#date.DAY-0…#` for a date range; `d-4040985-fn_…` as copied from a URL works); they are applied by the data source, so unlike Filters the rows are not re-checked. **Layout: Cards** shows one card per row (title, optional title link, subtitle, "label: value" details, body). Rows that don't match the filters are dropped even if the data source ignores filters (JSON API without "handle filters", BeanShell). The canvas shows headers only (or a starter's sample rows); Preview and Send test run the query. |
| Repeat | `mj-wrapper` per row | Data. The blocks inside it, once per row of a datalist, each drawn as a card (a wrapper with background, border, radius, inset by the side padding). The card takes content blocks **and Rows** (side-by-side columns, stacking on phones). Blocks inside use `{row.COLUMN}` (formatted text, escaped), `{row.COLUMN?html}` (formatted HTML, scripts stripped), `{row.COLUMN?raw}` and any row field such as `{row.id}`; in links a whole-field value is the URL and a field inside a URL is URL-encoded. Merge fields and row fields are filled in one pass, so row data is never resolved as merge fields and a merge value is never read as a row field. A Datalist Table inside, filtered by `{row.id}`, gives each card its own list. Top level only. Cards use a wrapper inside the email's own wrapper; MJML discourages this, but mjml4j renders it as plain nested tables (Outlook's included). Same filters, filter parameters, sort, row cap and run-as rule as the Datalist Table. On the canvas: the label shows the datalist's name and the row limit. While the card is empty or as dropped, **Fill from columns** puts the first column in as a bold title and the others as a Facts list (empty values hidden), in one undo step. Under the card, a read-only **Real rows** strip shows the first 3 rows rendered with the Test data: one line says how many were found, **Details** lists the filter values used, "no rows match" opens them by itself, and reaching the row limit says so (hide or show the strip; the choice is remembered). Preview's blue bar appears only for a Repeat or Datalist Table that found nothing or hit its limit. |
| Divider | `mj-divider` | |
| Spacer | `mj-spacer` | |
| Row | `mj-section` | Layout. Drops with two Columns. Top level, inside a Repeat's card, or in Outside the card. **Keep side by side on mobile** wraps the columns in `mj-group`, for Approve / Reject buttons and similar. |
| Column | `mj-column` | Layout. Takes a width, background, **border, corner radius** and padding, e.g. a bordered card. Only inside a Row. Stacks on mobile. |
| Outside the card | `mj-wrapper` | Layout. Its blocks and Rows sit on the page background: first in the email it is above the card (a logo), last it is below (address, "Questions?"), in the middle it splits the email into two cards. Top level only. Padding and optional background colour. |
| Steps | `mj-text` (tables) | Components. A status tracker: circles joined by a line, a label under each (up to 7). **Current step** is a number (0 = not started, above the last = all done) or a field such as `#form.request.status#`, matched (ignoring case) against each step's label and its **Also matches** values (`In review, Approved`). No match on a send: every step upcoming, with a warning. **Step shown in the builder** is used on the canvas while the value is a field, and in Preview when it is empty. **Reaching the last step completes it** (on by default). Done steps show a check mark or their number. Left empty, the accent and label colours follow the template theme. Circles are square in classic Outlook. |
| Progress bars | `mj-text` (tables) | Components. Labelled bars; each value is a percentage (`86`, `86%`) or a fraction (`12/20`, shown as "12 / 20"), typed or a field (`#form.project.done#/#form.project.total#`, `{row.pct}` in a Repeat). Clamped to 0–100 with a warning; a value that is not a number warns and shows 0%. **Value shown in the builder** as for Steps. Per-bar colour, remaining colour, height, rounded ends. |
| Facts list | `mj-text` (table) | Components. Label and value rows (STATUS / UPDATED BY, or a memo's TO / FROM / RE); values usually fields, HTML allowed. Labels in small capitals, plain or monospaced; value on the right or beside the label; the label column keeps its width (35% by default) either way; optional dividers, and rows sit closer without them. **Hide rows with an empty value** drops a row whose field came out empty (the canvas keeps it, since the field is unresolved there). Works in a Repeat with `{row.…}`. |
| Timeline | `mj-text` (table) | Components. Items down a line with a dot each: an optional time column (left out when no item has a time), a title and a detail line. Hollow or filled dots, an optional dot colour per item, monospaced times, and line, title and detail colours. The line is a cell border; classic Outlook leaves out the dots. |
| Stat tiles | `mj-text` (table) | Components. Up to 4 big numbers side by side (a number can be a field), each with an optional icon above it, a caption, its own background and number colour; optional border, radius, gap. One table row, so tiles are always the same height. On phones they stack, one under another (a media query at MJML's 480px breakpoint; apps that ignore it keep them side by side); **Keep side by side on mobile** is for short numbers. |
| Badge | `mj-text` (table) | Components. A short label in a pill (a status from a field), a circle (initials, an icon; set its size) or a rounded square. Aligned by a full-width cell, so it also works right-aligned in a Row's column. |
| Callout | `mj-text` (table) | Components. Icon, title and text in a soft box, a box with a coloured left bar (a warning), a dashed box or a quote (the title becomes the attribution). **Large code text** shows the text big, monospaced and spaced out (a one-time code from a field). |
| Hero | `mj-hero` | Layout. Headline, text and an optional button over a background image, full width. The picker accepts an App Resource, which is **linked** (not embedded: Gmail and others don't load `cid:` backgrounds) through the template's **Public server address** setting, so the resource must be public; or an absolute URL. Top level only. Emits a VML background for classic Outlook; the background colour shows while images are blocked, so keep the text readable on it. |

Top-level blocks are wrapped in a full-width section automatically (Rows and Heroes are sections already). Use a Row for multi-column layouts or cards.

**Template theme:** page and content backgrounds, text and link colours, font, width, and the **card** settings: corner radius, where coloured bands are clipped to the corners, and an optional shadow. Classic Outlook shows square corners and no shadow.

`email-template-builder/src/test/resources/golden/daily-update.json` rebuilds a planner's "Daily Update" email: a green header band, the date, a bordered digest card from a merge field in a Custom HTML block, and a grey footer, in a rounded, shadowed card. To view the compiled snapshots, open the `.html` files next to them in a browser.

## Starter templates

There are 27 starters in `email-template-builder/src/main/resources/templates/starters/` (one JSON each, plus `index.json` with labels, categories, and sample values for merge fields and template variables). Each has its own layout idea, not just its own colours:
- Basic: Blank
- IT & workflow: Notification (status tracker), Approval request (decision card), Password reset (security code), Daily digest (Repeat of update cards), Alert / incident (incident log with timeline), Weekly task report (Datalist Table cards), Month at a glance (KPI tiles, progress bars), Bold notice (brutalist), Build report (terminal), Meeting agenda (timeline)
- Newsletter: Newsletter (Navbar, Hero), Editorial (serif magazine)
- Corporate: Announcement (memo), HR policy update (change checklist), Event invitation (poster), Welcome / onboarding (logo and footer outside the card), Receipt (Datalist Table line items), Feedback request (1 to 5 scale), Event ticket, Quarterly update (Swiss grid), Achievement (playful badge)
- Marketing: Promotion (Hero), Feature announcement (Hero), Midnight launch (dark bento grid), We miss you (sunset bands), VIP invitation (black and gold). Unsubscribe links must point at a real opt-out in your app.

The starters use the component blocks wherever a part has one: status pills and initials are Badges, Notification's tracker is Steps (from `#form.request.status#`) with a Facts list, Alert and Meeting agenda use Timeline, KPI uses Stat tiles and Progress bars, Password reset's code, Editorial's pull quote and the agenda's note are Callouts, Announcement's memo header is a Facts list, Achievement's stats are Stat tiles. So they are edited in properties, not HTML. Layout tiles that hold free text (Approval, Policy, Midnight, Bold notice, Build report, KPI highlights) stay Text blocks on a tile background; their text is edited inline.

Design notes: tiles are drawn inside their columns (column padding is the gutter) with a min-height where rows should match; classic Outlook ignores rounded corners and min-height. The starters use no emoji (they look informal, and some email apps draw them flat or as boxes); icon places hold a symbol, a number or a short word.

Data starters ship with **sample rows** (`sampleRows` on the Datalist Table or Repeat): shown only in the builder (gallery thumbnails, canvas, Preview, Send test) until a datalist is chosen, never in a real send. Picking a starter with template variables fills its Test data with the gallery's sample values.

`StarterCatalogTest` checks every starter:
- it renders as its gallery thumbnail does without warnings, apart from image placeholders and sample-row notes, and with no unfilled `{var.}`/`{row.}` field
- every merge field it uses has a sample
- it stays under 60 KB
- its HTML contains no literal MJML (`<mj-`)

The test also writes `email-template-builder/target/starter-previews/ID.html` for a visual check in a browser.

## Components gallery

**Components** (toolbar) opens a gallery of ready-made parts with live thumbnails, to insert into the current design. There are 24, in four groups:
- Progress: Steps (status from a field, numbered, five stages) and Progress bars (goals, from fields, one thick bar)
- Headers & footers: a header with a status Badge, a logo above the card, two footers
- Highlights: Stat tiles (soft, outlined), Facts lists (details, memo header), Timelines (agenda, status updates), Callouts (note, warning, code box, pull quote), a coupon, a signature with an initials Badge
- Actions: two buttons (accept or decline), a button with a note

Each component is **one element**: a component block (Steps, Progress bars, Facts list, Timeline, Stat tiles, Badge, Callout) set up in one look, or a Row / Outside block of ordinary blocks. Everything is edited in properties; there is no hand-written HTML to edit. Components never use `{var.…}` or `{row.…}` (a template may not declare the variable), only merge fields and typed values. It goes where the builder's own paste would put it:
- after the selected block, at the first level that accepts it: with a block in a column selected, a content component goes after it in that column, and a Row component goes after the Row that holds it
- into a selected Column
- with nothing selected, at the end of the card, before the blocks under it; the logo goes to the very top and the footers to the very end

It is one undo step. The component blocks are also in the palette under **Components**.

They are in `email-template-builder/src/main/resources/templates/components/` (one JSON each, plus `index.json`). `StarterCatalogTest` renders each on its own, as its thumbnail does, with no warnings and no merge field without a sample, and writes `email-template-builder/target/component-previews/ID.html` plus an `index.html` contact sheet. Serve that folder to view the sheet, e.g. `python3 -m http.server --directory email-template-builder/target/component-previews`, since it loads the previews in frames.

## AI Composer (optional)

With agent-builder installed, the builder gets agent-builder's **AI Composer** chat, the same one its Form, List, UI and Theme builders have:
- Describe an email and it builds one, preferring a starter template or components and adapting them.
- Select a block and say how to change it, and it changes only that block.
- Every change previews on the canvas. **Apply** keeps the result as one undo step; **Cancel** discards it.
- It doesn't invent facts. Figures, dates and names it can't know become merge fields, or template variables with a sample value, and its reply lists the variables it added. The sample values (and a used starter's) go into **Test data**, only for variables that have no test value yet, so Preview and Send test show a realistic email. The saved template keeps only the variables' names and descriptions.
- It uses no emoji unless asked: icon places get a number, a short word or a plain symbol.
- It designs for phones: label/value pairs in a Facts list rather than two columns, side by side only for short content, headings sized so long words fit.
- It replaces a starter's or component's placeholder content (names, numbers, dates) instead of keeping it, and uses `use_starter` / `set_blocks` only for a new email.

**Requirements:**
- Joget DX 9 Enterprise edition.
- An AI service set up in **AI Central Config**.
- agent-builder built from `8.2-SNAPSHOT` with `org.joget.ai.agent.lib.composer` added to its bundle's `Export-Package`. That one line is the only change it needs; released versions don't export that package yet.

**Install order:** install agent-builder first, then this plugin. This plugin registers the composer when it starts, so install it again (or restart it) after installing agent-builder. Without agent-builder, or on the Community edition, the composer is simply off and the builder works as before. The plugin log says why it is off: agent-builder isn't installed, the installed build doesn't export `org.joget.ai.agent.lib.composer` (as released versions don't yet), or its composer classes don't match this plugin.

**Who can use it, and what it sends:**
- DX 9 lets anyone open a plugin's WebSocket, and agent-builder's base chat checks no role, so the composer checks every message itself. An anonymous user gets an error and the socket closes (code 1008).
- Each request also needs the right to design its app: an admin, or a user DX found may edit that app (an App Designer or app creator). DX's own check needs an HTTP request, which a WebSocket message hasn't, so the composer reads the answer DX caches in the HTTP session (`USER_EDITABLE_APPS`) when the user opens the app's builder. `mayDesign` has the rule, unit-tested on its own.
- A request for an app that doesn't exist is refused.
- Each run gets its own `PluginThread`, made on the socket's thread, rather than going to agent-builder's pooled executor. The platform carries the tenant profile to it on every DX 9 version, and the user too from DX 9.1. `asUser` sets the user only where the platform didn't (DX 9.0, 9.1.1.1). DX Cloud refuses plugin code that sets the profile or the security context on a plain pooled thread, and a reused pool thread keeps the tenant of whichever thread created it. Don't move runs back to the executor.
- The greeting says what goes to the AI service: the user's messages, attached images and the template. Test data stays in the browser, and `check_email` renders in builder mode, so no datalist rows are read.

**Run handling:**
- A new request stops the one still running on that chat, so two runs never write to one canvas.
- The canvas previews after a step that changed the email, not after one that only read it.
- Stop after changes offers Apply or Cancel, as an error does, so the half-made email can be undone.
- A step's result longer than 2,000 characters reaches the chat as a short JSON summary (`traceResponse`), never cut mid-JSON, which would drop the step and its preview.

**What the AI can do** (`EmailTemplateComposerTool`):
- **Read:** the template, the block catalog and each block's properties. These are built from the installed blocks' own property pages, so third-party blocks are described too.
- **Start from ready-made parts:** use a starter, or insert a component.
- **Change the template:** set the template's properties, and add, update, move or remove blocks.
- **`check_email`:** renders the draft with this plugin's own renderer and reports problems (blocks in the wrong place, blocked links, unfilled `{var.…}`, a missing subject, size) so the AI fixes them before you see the result. It also estimates what won't fit a phone: a heading whose longest word is wider than a 360px phone, three or more columns of text kept side by side, and Stat tiles with long numbers kept side by side. These checks are kept conservative, and no starter triggers them.

**How it plugs into agent-builder:**
- `EmailTemplateComposerChat` extends agent-builder's `AbstractComposerChat`.
- Its `getHtml()` renders agent-builder's own chat template and assets, then points the chat's `classPath` (its WebSocket address) at this plugin. Keep its template data in step with `AbstractComposerChat.getHtml()` when agent-builder changes; if the template's `"classPath"` setting ever changes, the chat is left out (with a log warning) rather than connecting to the wrong socket.
- It sends the chat's messages in the format agent-builder's JS reads.
- The canvas gives every block an `id` when a design loads, so the chat can find the selected block.
- Its `getBridgeJs()` is agent-builder's bridge plus one step: the sample values sent beside the template (`etbTestValues`) are taken off before the template loads, and go to Test data.

**Building it:**
- The composer classes (`org.joget.etb.composer`) compile only with the `ai-composer` Maven profile.
- The profile switches on automatically when `org.joget.ai.agent:agent-builder-api:8.2-SNAPSHOT` is in your local Maven repository. Run `mvn install` in agent-builder first.
- It adds the build-time module `agent-builder-api-jakarta`, which converts that API to jakarta with the Eclipse Transformer. DX 9 converts agent-builder the same way on install, and the composer must compile against those signatures. Nothing from agent-builder is embedded.
- Build without the composer with `-P'!ai-composer'`.

**Not yet:**
- Stop takes effect at the AI's next step.
- The composer isn't offered to agent-builder's orchestrator, whose composer list is hard-coded.
- Switching templates doesn't reset the chat. agent-builder takes the builder id from the URL, which is `cbuilder` for every custom builder.

## Development notes

### Canvas vs Preview parity

The canvas renders every block with the same mjml4j compiler as Preview. It still depends on canvas-side CSS: rows and columns, a text-only reset of the builder iframe's Bootstrap styles, and no side padding on the canvas body. After changing that CSS, compare the starters on the canvas with Preview, on desktop and mobile.

Expected differences: Digest's canvas shows the Repeat's card once, Preview a card per sample row. A few starters (KPI, Receipt, Feedback, Password reset, VIP) run slightly taller on the canvas, where a `{var.…}` or `#token#`, shown unfilled, wraps where its sample value would not. Containers, blocks and inline text get their minimum drop height only while empty: fixed minimums had made short Columns, thin Dividers and a 2px rule taller than in the email. The canvas sets `box-sizing: content-box` inside blocks: Bootstrap's `border-box` had drawn a 300px Hero 224px tall.

### DX 9 gotchas this code works around

- **`ExtDefaultPlugin.setProperties` wraps properties in `HashVariableSupportedMap`**, which resolves hash variables on every `get()` with **no escaping** and the thread's "current" assignment. `DefaultEmailBlock` stores a plain map instead, so the render context does all resolution. *Found by the tests.*
- **Don't list `org.json` or `jakarta.servlet` in `Import-Package`.** bnd pins the build jar's version, DX 9 exports them unversioned, and the bundle fails to install. They come in through `DynamicImport-Package`.
- **Stale builder JS after an upload.** Joget passes an empty `buildNumber` to `getBuilderJS`, so the script URL never changed and browsers kept the old file. `EmailTemplateBuilder.BUILD_STAMP` changes on every install.
- **App Resource URLs without a version serve the published version.** `/web/app/APP/resources/NAME` resolves to the published version's file, so the builder's canvas and Preview link to `/web/app/APP/VERSION/resources/NAME`, the version being edited; a new logo in an unpublished version would otherwise not show. A Hero background's public URL in a sent email stays version-less, so it keeps working after a newer version is published.
- **mjml4j and nested tables without `<tbody>`.** A table nested in another table's cell, with its rows directly inside it (no `<tbody>`, as most hand-written and pasted HTML has), made mjml4j lose the enclosing tags: everything after that `mj-text` came out as literal MJML (a Row's second column vanished). The renderer adds an explicit `<tbody>` to every such table before compiling, for all blocks including Text and HTML. `StarterCatalogTest` fails any starter or component whose HTML still contains `<mj-`.
- **Canvas parity:**
  - Blocks are body fragments, so MJML's `<head>` base CSS is missing and the builder iframe's Bootstrap leaks in (for example `p` margins).
  - Revert Bootstrap on **text elements only**. Never revert tables, cells or images: MJML lays them out with HTML attributes (`align`, `width`, `bgcolor`), which any CSS rule overrides.
  - The canvas body's 25px side padding made phone-width text wrap early.
- **Canvas:**
  - Every block needs `builderTemplate.dragHtml`, or the palette drag finds no drop zone.
  - A viewport switch silently drops unapplied edits; the canvas JS applies them first.
  - Inline TinyMCE instances are destroyed before a block re-renders.
  - The builder's `editor-helpers.css` turns off clicks on every link and button inside a block, so a design's links can't navigate. The canvas's own controls inside a Repeat (Fill from columns, Details, Hide) switch them back on.

### Live check: the Email Tool route and `?noescape`

This needs a DX 9 server with SMTP, or a local mail catcher such as Mailpit. It shows why `?noescape` is required and that merge values stay escaped inside a template.

1. Install the jar. In an app with a process, create a template, say `ET-xxx`.
2. Edit its first Text block to include: `Activity: #assignment.activityName# / Payload: #variable.payload#`. Here `payload` is a workflow variable of the process.
3. Set up two Email Tool activities, both with **HTML** ticked:
   - **A:** message `#emailTemplate.ET-xxx?noescape#`, subject `#emailTemplate.ET-xxx.subject#`
   - **B:** message `#emailTemplate.ET-xxx#`
4. Before them, set `payload` to `<a href="https://evil.example/login">Reset your password</a>` (for example with a form field mapped to it), then run the process.

**Expected results:**
- **A:**
  - correct layout
  - the activity name filled in
  - the payload shown as literal text, not as a link
  - the subject taken from the template (Gmail may still auto-link bare URLs inside the escaped text)
- **B:** broken layout, with title text leaking and alignment lost. This is why `?noescape` is required.
